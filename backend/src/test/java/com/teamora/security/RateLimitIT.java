package com.teamora.security;

import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import java.util.concurrent.atomic.AtomicInteger;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Auth endpoint rate limits (429 + Retry-After). Runs in its own context with tiny
 * limits; every scenario uses its own client IP so they don't share buckets.
 */
@TestPropertySource(properties = {
        "teamora.rate-limit.login-per-email.max=3",
        "teamora.rate-limit.login-per-email.window=15m",
        "teamora.rate-limit.login-per-ip.max=5",
        "teamora.rate-limit.login-per-ip.window=15m",
        "teamora.rate-limit.register-per-ip.max=2",
        "teamora.rate-limit.register-per-ip.window=1m",
        "teamora.rate-limit.forgot-password-per-ip.max=2",
        "teamora.rate-limit.forgot-password-per-ip.window=1m",
        "teamora.rate-limit.reset-password-per-ip.max=2",
        "teamora.rate-limit.reset-password-per-ip.window=15m",
})
class RateLimitIT extends AbstractIntegrationTest {

    private static final AtomicInteger NEXT_IP = new AtomicInteger(1);

    private static RequestPostProcessor from(String ip) {
        return r -> {
            r.setRemoteAddr(ip);
            return r;
        };
    }

    private static String freshIp() {
        int n = NEXT_IP.getAndIncrement();
        return "10.77." + (n / 250) + "." + (n % 250 + 1);
    }

    private ResultActions login(String ip, String email, String password) throws Exception {
        return mvc.perform(post("/api/auth/login").with(from(ip)).contentType("application/json")
                .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)));
    }

    private void assertLimited(ResultActions r) throws Exception {
        r.andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.status").value(429))
                .andExpect(jsonPath("$.message").value(containsString("Too many attempts")));
    }

    @Test
    void login_perEmail_locksThatAccountOnly_evenAcrossIps() throws Exception {
        for (int i = 0; i < 3; i++) {
            login(freshIp(), "weijie@lumi.com", "wrong-password").andExpect(status().isUnauthorized());
        }
        // 4th try — even with the right password, from a new IP — is refused.
        assertLimited(login(freshIp(), "weijie@lumi.com", "password"));
        // Any casing counts as the same account.
        assertLimited(login(freshIp(), "WeiJie@Lumi.com", "password"));
        // Other accounts are unaffected.
        login(freshIp(), "amir@lumi.com", "password").andExpect(status().isOk());
    }

    @Test
    void login_perIp_capsSprayingAcrossAccounts() throws Exception {
        String ip = freshIp();
        for (int i = 0; i < 5; i++) {
            login(ip, "nobody-" + i + "@example.com", "x").andExpect(status().isUnauthorized());
        }
        assertLimited(login(ip, "sarah@lumi.com", "password"));
        login(freshIp(), "sarah@lumi.com", "password").andExpect(status().isOk());
    }

    @Test
    void register_perIp() throws Exception {
        String ip = freshIp();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/auth/register").with(from(ip)).contentType("application/json")
                            .content("{\"companyName\":\"RL %d\",\"fullName\":\"RL\",\"email\":\"rl-%d-%d@example.com\",\"password\":\"password\"}"
                                    .formatted(i, i, System.nanoTime())))
                    .andExpect(status().isOk());
        }
        assertLimited(mvc.perform(post("/api/auth/register").with(from(ip)).contentType("application/json")
                .content("{\"companyName\":\"RL x\",\"fullName\":\"RL\",\"email\":\"rl-x-%d@example.com\",\"password\":\"password\"}"
                        .formatted(System.nanoTime()))));
    }

    @Test
    void forgotAndResetPassword_perIp() throws Exception {
        String ip = freshIp();
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/auth/forgot-password").with(from(ip)).contentType("application/json")
                            .content("{\"email\":\"nobody@example.com\"}"))
                    .andExpect(status().isOk());
        }
        assertLimited(mvc.perform(post("/api/auth/forgot-password").with(from(ip)).contentType("application/json")
                .content("{\"email\":\"nobody@example.com\"}")));

        String ip2 = freshIp();
        String body = "{\"email\":\"nobody@example.com\",\"code\":\"000000\",\"newPassword\":\"new-password-1\"}";
        for (int i = 0; i < 2; i++) {
            mvc.perform(post("/api/auth/reset-password").with(from(ip2)).contentType("application/json").content(body))
                    .andExpect(status().isBadRequest());
        }
        assertLimited(mvc.perform(post("/api/auth/reset-password").with(from(ip2)).contentType("application/json")
                .content(body)));
    }
}
