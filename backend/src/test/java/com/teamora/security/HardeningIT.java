package com.teamora.security;

import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.matchesPattern;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Errors, request ids, body limits, input limits, health, CORS, emails, push tokens. */
class HardeningIT extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    // ---- errors ----

    @Test
    void unknownPath_404_json() throws Exception {
        String sarah = login("sarah@lumi.com", "password");
        mvc.perform(get("/api/does-not-exist").header("Authorization", bearer(sarah)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404));
    }

    @Test
    void wrongMethod_405_and_missingParam_400() throws Exception {
        String sarah = login("sarah@lumi.com", "password");
        mvc.perform(put("/api/admin/dashboard").header("Authorization", bearer(sarah)))
                .andExpect(status().isMethodNotAllowed())
                .andExpect(jsonPath("$.status").value(405));
        mvc.perform(get("/api/admin/payroll/run").header("Authorization", bearer(sarah)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("period")));
    }

    @Test
    void unauthenticated_401_isJson() throws Exception {
        mvc.perform(get("/api/employees/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.status").value(401));
    }

    // ---- request id ----

    @Test
    void requestId_isEchoed_orGenerated() throws Exception {
        mvc.perform(get("/actuator/health").header("X-Request-Id", "app-abc-123"))
                .andExpect(header().string("X-Request-Id", "app-abc-123"));
        mvc.perform(get("/actuator/health"))
                .andExpect(header().string("X-Request-Id", matchesPattern("[0-9a-f]{16}")));
        // Junk (header injection attempts, overlong values) is replaced, not echoed.
        mvc.perform(get("/actuator/health").header("X-Request-Id", "bad id;\nX-Evil: 1"))
                .andExpect(header().string("X-Request-Id", matchesPattern("[0-9a-f]{16}")));
    }

    // ---- body + field limits ----

    @Test
    void oversizedBody_413_evenOnPublicEndpoints() throws Exception {
        String big = "{\"email\":\"%s@example.com\",\"password\":\"x\"}".formatted("a".repeat(3_700_000));
        mvc.perform(post("/api/auth/login").contentType("application/json").content(big))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.status").value(413));
    }

    @Test
    void freeTextAndAmountLimits_400() throws Exception {
        String amir = login("amir@lumi.com", "password");
        mvc.perform(post("/api/claims").header("Authorization", bearer(amir)).contentType("application/json")
                        .content("{\"category\":\"MEAL\",\"title\":\"%s\",\"amount\":10,\"claimDate\":\"2026-09-15\"}"
                                .formatted("t".repeat(121))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.title").exists());
        mvc.perform(post("/api/claims").header("Authorization", bearer(amir)).contentType("application/json")
                        .content("{\"category\":\"MEAL\",\"title\":\"Lunch\",\"amount\":100000.01,\"claimDate\":\"2026-09-15\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.amount").exists());
        mvc.perform(post("/api/overtime").header("Authorization", bearer(amir)).contentType("application/json")
                        .content("{\"workDate\":\"2026-09-12\",\"hours\":2,\"reason\":\"%s\"}".formatted("r".repeat(501))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason").exists());
        String sarah = login("sarah@lumi.com", "password");
        mvc.perform(post("/api/employees").header("Authorization", bearer(sarah)).contentType("application/json")
                        .content("{\"fullName\":\"X\",\"email\":\"lim-%d@example.com\",\"password\":\"password\",\"role\":\"EMPLOYEE\",\"nric\":\"%s\"}"
                                .formatted(System.nanoTime(), "9".repeat(21))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.nric").exists());
    }

    // ---- actuator ----

    @Test
    void health_isPublic_upOnly_noDetails() throws Exception {
        mvc.perform(get("/actuator/health"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UP"))
                .andExpect(jsonPath("$.components").doesNotExist())
                .andExpect(jsonPath("$.details").doesNotExist());
        // Nothing else from actuator is reachable.
        String sarah = login("sarah@lumi.com", "password");
        mvc.perform(get("/actuator/env").header("Authorization", bearer(sarah)))
                .andExpect(status().isNotFound());
    }

    // ---- CORS ----

    @Test
    void wildcardCors_neverAllowsCredentials() throws Exception {
        mvc.perform(options("/api/auth/login")
                        .header("Origin", "https://evil.example")
                        .header("Access-Control-Request-Method", "POST"))
                .andExpect(header().string("Access-Control-Allow-Origin", "*"))
                .andExpect(header().doesNotExist("Access-Control-Allow-Credentials"));
    }

    // ---- emails ----

    @Test
    void emails_areStoredLowercase_andUniqueIgnoringCase() throws Exception {
        long n = System.nanoTime();
        String mixed = "  Mixed.Case-" + n + "@Example.COM ";
        mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content("{\"companyName\":\"Case Co\",\"fullName\":\"Case Owner\",\"email\":\"%s\",\"password\":\"password\"}"
                                .formatted(mixed.trim())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employee.email").value("mixed.case-" + n + "@example.com"));
        // Sign in with any casing.
        login("MIXED.CASE-" + n + "@EXAMPLE.COM", "password");
        // Same address in another case → refused.
        mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content("{\"companyName\":\"Case Co 2\",\"fullName\":\"Dup\",\"email\":\"mixed.case-%d@example.com\",\"password\":\"password\"}"
                                .formatted(n)))
                .andExpect(status().isBadRequest());
        // The database enforces it too.
        Long dupes = jdbc.queryForObject("SELECT count(*) FROM employees WHERE lower(email) = ?", Long.class,
                "mixed.case-" + n + "@example.com");
        assertThat(dupes).isEqualTo(1);
    }

    // ---- push tokens ----

    @Test
    void removePushToken_onlyDeletesYourOwn() throws Exception {
        String amir = login("amir@lumi.com", "password");
        String weijie = login("weijie@lumi.com", "password");
        String token = "ExponentPushToken[own-" + System.nanoTime() + "]";
        mvc.perform(post("/api/notifications/push-token").header("Authorization", bearer(amir))
                        .contentType("application/json").content("{\"token\":\"%s\",\"platform\":\"ios\"}".formatted(token)))
                .andExpect(status().isNoContent());

        // Someone else can't unregister Amir's device.
        mvc.perform(delete("/api/notifications/push-token").param("token", token).header("Authorization", bearer(weijie)))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM push_tokens WHERE token = ?", Long.class, token)).isEqualTo(1);

        mvc.perform(delete("/api/notifications/push-token").param("token", token).header("Authorization", bearer(amir)))
                .andExpect(status().isNoContent());
        assertThat(jdbc.queryForObject("SELECT count(*) FROM push_tokens WHERE token = ?", Long.class, token)).isZero();
        mvc.perform(delete("/api/notifications/push-token").param("token", token))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void errorBodies_neverLeakInternals() throws Exception {
        // A malformed UUID path param → 400 with a plain message, no exception text.
        String sarah = login("sarah@lumi.com", "password");
        mvc.perform(get("/api/employees/not-a-uuid").header("Authorization", bearer(sarah)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(not(containsString("Exception"))));
    }
}
