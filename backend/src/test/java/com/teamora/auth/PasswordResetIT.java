package com.teamora.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamora.AbstractIntegrationTest;
import com.teamora.notification.PushTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Forgot password by one-time code (public endpoints). The test profile turns on
 * {@code teamora.password-reset.expose-code}, so the code comes back as
 * {@code devCode}. Every scenario registers its own company, so the seeded demo
 * accounts keep their password.
 */
class PasswordResetIT extends AbstractIntegrationTest {

    private static final String GENERIC = "That code is incorrect or has expired.";

    @Autowired
    private PasswordResetCodeRepository codes;
    @Autowired
    private PushTokenRepository pushTokens;
    @Autowired
    private com.teamora.employee.EmployeeRepository employees;

    /** A fresh account: its email + employee id + first session. */
    private record Account(String email, UUID id, JsonNode session) {}

    private Account account(String tag) throws Exception {
        String slug = tag + System.nanoTime();
        String email = "reset-" + slug + "@example.com";
        String body = """
                {"companyName":"Reset %s","fullName":"Owner %s","email":"%s","password":"password"}"""
                .formatted(slug, slug, email);
        var res = mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn();
        JsonNode session = om.readTree(res.getResponse().getContentAsString());
        return new Account(email, UUID.fromString(session.get("employee").get("id").asText()), session);
    }

    private ResultActions forgot(String email) throws Exception {
        return mvc.perform(post("/api/auth/forgot-password").contentType("application/json")
                .content("{\"email\":\"%s\"}".formatted(email)));
    }

    /** Request a code and return it (asserts 200 + a 6-digit devCode). */
    private String requestCode(String email) throws Exception {
        var res = forgot(email).andExpect(status().isOk()).andReturn();
        String code = om.readTree(res.getResponse().getContentAsString()).get("devCode").asText();
        assertTrue(code.matches("\\d{6}"), "expected a 6-digit code but got " + code);
        return code;
    }

    private ResultActions reset(String email, String code, String newPassword) throws Exception {
        return mvc.perform(post("/api/auth/reset-password").contentType("application/json")
                .content("{\"email\":\"%s\",\"code\":\"%s\",\"newPassword\":\"%s\"}".formatted(email, code, newPassword)));
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/auth/refresh").contentType("application/json")
                .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)));
    }

    private void expectLoginFails(String email, String password) throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isUnauthorized());
    }

    /** A code that is guaranteed not to be the real one. */
    private static String wrong(String code) {
        return code.equals("000000") ? "000001" : "000000";
    }

    @Test
    void happyPath_newPasswordWorks_oldFails_sessionsAndPushTokensGone() throws Exception {
        Account a = account("ok");
        String pushToken = "ExponentPushToken[reset-" + System.nanoTime() + "]";
        mvc.perform(post("/api/notifications/push-token")
                        .header("Authorization", bearer(a.session().get("accessToken").asText()))
                        .contentType("application/json").content("{\"token\":\"%s\",\"platform\":\"ios\"}".formatted(pushToken)))
                .andExpect(status().isNoContent());
        assertEquals(1, pushTokens.findByEmployeeId(a.id()).size());

        // Public endpoints: no Authorization header anywhere below.
        String code = requestCode(a.email());
        reset(a.email(), code, "brand-new-pw").andExpect(status().isNoContent());

        login(a.email(), "brand-new-pw");
        expectLoginFails(a.email(), "password");
        refresh(a.session().get("refreshToken").asText()).andExpect(status().isBadRequest());
        assertTrue(pushTokens.findByEmployeeId(a.id()).isEmpty());
    }

    @Test
    void emailIsCaseInsensitive() throws Exception {
        Account a = account("case");
        String code = requestCode(a.email().toUpperCase());
        reset(a.email().toUpperCase(), code, "brand-new-pw").andExpect(status().isNoContent());
        login(a.email(), "brand-new-pw");
    }

    @Test
    void unknownEmail_still200_withNullDevCode() throws Exception {
        String unknown = "nobody-" + System.nanoTime() + "@example.com";
        forgot(unknown).andExpect(status().isOk()).andExpect(jsonPath("$.devCode").value(nullValue()));
        // …and redeeming against it fails exactly like a wrong code.
        reset(unknown, "123456", "brand-new-pw").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(GENERIC));
    }

    @Test
    void wrongCode_400_andPasswordUnchanged() throws Exception {
        Account a = account("wrong");
        String code = requestCode(a.email());

        reset(a.email(), wrong(code), "brand-new-pw").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(GENERIC));
        login(a.email(), "password");
        expectLoginFails(a.email(), "brand-new-pw");

        // One wrong guess doesn't burn the code.
        reset(a.email(), code, "brand-new-pw").andExpect(status().isNoContent());
    }

    @Test
    void validation_shortPassword_400_withoutBurningAnAttempt() throws Exception {
        Account a = account("val");
        String code = requestCode(a.email());

        reset(a.email(), code, "short").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());
        mvc.perform(post("/api/auth/forgot-password").contentType("application/json").content("{}"))
                .andExpect(status().isBadRequest());

        reset(a.email(), code, "brand-new-pw").andExpect(status().isNoContent());
    }

    @Test
    void expiredCode_400() throws Exception {
        Account a = account("exp");
        String code = requestCode(a.email());

        PasswordResetCode row = codes.findFirstByEmployeeIdAndUsedAtIsNullOrderByCreatedAtDesc(a.id()).orElseThrow();
        // Issued with a 10 minute life.
        long ttlSeconds = row.getExpiresAt().getEpochSecond() - row.getCreatedAt().getEpochSecond();
        assertEquals(600, ttlSeconds);
        row.setExpiresAt(Instant.now().minusSeconds(1));
        codes.save(row);

        reset(a.email(), code, "brand-new-pw").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(GENERIC));
        login(a.email(), "password");
    }

    @Test
    void fiveWrongAttempts_killTheCode() throws Exception {
        Account a = account("lock");
        String code = requestCode(a.email());

        for (int i = 0; i < 5; i++) {
            reset(a.email(), wrong(code), "brand-new-pw").andExpect(status().isBadRequest());
        }
        // Even the right code is dead now.
        reset(a.email(), code, "brand-new-pw").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(GENERIC));
        login(a.email(), "password");

        // A fresh code works again.
        String fresh = requestCode(a.email());
        reset(a.email(), fresh, "brand-new-pw").andExpect(status().isNoContent());
        login(a.email(), "brand-new-pw");
    }

    @Test
    void codeIsSingleUse() throws Exception {
        Account a = account("once");
        String code = requestCode(a.email());

        reset(a.email(), code, "brand-new-pw").andExpect(status().isNoContent());
        reset(a.email(), code, "another-new-pw").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(GENERIC));
        login(a.email(), "brand-new-pw");
    }

    @Test
    void newCodeInvalidatesThePreviousOne() throws Exception {
        Account a = account("sup");
        String first = requestCode(a.email());
        String second = requestCode(a.email());

        if (!first.equals(second)) {
            reset(a.email(), first, "brand-new-pw").andExpect(status().isBadRequest());
        }
        reset(a.email(), second, "brand-new-pw").andExpect(status().isNoContent());
    }

    @Test
    void rateLimit_threeRequestsPer15Minutes() throws Exception {
        Account a = account("rate");
        requestCode(a.email());
        requestCode(a.email());
        String third = requestCode(a.email());

        // The 4th still answers 200 but creates nothing…
        forgot(a.email()).andExpect(status().isOk()).andExpect(jsonPath("$.devCode").value(nullValue()));
        assertEquals(3, codes.countByEmployeeIdAndCreatedAtAfter(a.id(), Instant.now().minusSeconds(900)));
        // …so the 3rd code is still the live one.
        reset(a.email(), third, "brand-new-pw").andExpect(status().isNoContent());
    }

    @Test
    void onlyAHashOfTheCodeIsStored() throws Exception {
        Account a = account("hash");
        String code = requestCode(a.email());
        PasswordResetCode row = codes.findFirstByEmployeeIdAndUsedAtIsNullOrderByCreatedAtDesc(a.id()).orElseThrow();
        assertNotEquals(code, row.getCodeHash());
        assertTrue(row.getCodeHash().startsWith("$2"), "expected a BCrypt hash");
    }

    @Test
    void deactivatedAccount_getsNoCode() throws Exception {
        Account a = account("off");
        String owner = a.session().get("accessToken").asText();
        String email = "reset-inactive-" + System.nanoTime() + "@example.com";
        var res = mvc.perform(post("/api/employees").header("Authorization", bearer(owner))
                        .contentType("application/json")
                        .content("{\"fullName\":\"Gone\",\"email\":\"%s\",\"password\":\"password\",\"role\":\"EMPLOYEE\"}".formatted(email)))
                .andExpect(status().isCreated()).andReturn();
        String id = om.readTree(res.getResponse().getContentAsString()).get("id").asText();
        var gone = employees.findById(UUID.fromString(id)).orElseThrow();
        gone.setActive(false);
        employees.save(gone);

        forgot(email).andExpect(status().isOk()).andExpect(jsonPath("$.devCode").value(nullValue()));
    }
}
