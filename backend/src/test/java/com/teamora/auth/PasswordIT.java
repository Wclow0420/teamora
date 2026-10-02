package com.teamora.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Self-service change-password + admin reset-password. Every scenario builds its
 * own company so the shared seeded accounts keep their demo password.
 */
class PasswordIT extends AbstractIntegrationTest {

    private JsonNode register(String slug) throws Exception {
        String body = """
                {"companyName":"Pw %s","fullName":"Owner %s","email":"pw-owner-%s@example.com","password":"password"}"""
                .formatted(slug, slug, slug);
        var res = mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(res.getResponse().getContentAsString());
    }

    /** Create an employee with the given role; returns its id. */
    private String createEmp(String ownerToken, String email, String role) throws Exception {
        String body = "{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"password\",\"role\":\"%s\"}"
                .formatted(email, email, role);
        var res = mvc.perform(post("/api/employees").header("Authorization", bearer(ownerToken))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn();
        return om.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    /** Full login response (access + refresh tokens). */
    private JsonNode session(String email, String password) throws Exception {
        var res = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(res.getResponse().getContentAsString());
    }

    private void expectLoginFails(String email, String password) throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password)))
                .andExpect(status().isUnauthorized());
    }

    private org.springframework.test.web.servlet.ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/auth/refresh").contentType("application/json")
                .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)));
    }

    private org.springframework.test.web.servlet.ResultActions change(String token, String body) throws Exception {
        return mvc.perform(post("/api/auth/change-password").header("Authorization", bearer(token))
                .contentType("application/json").content(body));
    }

    private org.springframework.test.web.servlet.ResultActions reset(String token, String id, String newPassword) throws Exception {
        return mvc.perform(post("/api/employees/" + id + "/reset-password").header("Authorization", bearer(token))
                .contentType("application/json").content("{\"newPassword\":\"%s\"}".formatted(newPassword)));
    }

    // ---------- change-password ----------

    @Test
    void changePassword_unauthenticated_401() throws Exception {
        mvc.perform(post("/api/auth/change-password").contentType("application/json")
                        .content("{\"currentPassword\":\"password\",\"newPassword\":\"brand-new-pw\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void changePassword_validation() throws Exception {
        String slug = "v" + System.nanoTime();
        String owner = register(slug).get("accessToken").asText();
        String email = "pw-emp-" + slug + "@example.com";
        createEmp(owner, email, "EMPLOYEE");
        String emp = login(email, "password");

        // Wrong current password.
        change(emp, "{\"currentPassword\":\"not-it\",\"newPassword\":\"brand-new-pw\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Current password is incorrect"));
        // Too short.
        change(emp, "{\"currentPassword\":\"password\",\"newPassword\":\"short\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());
        // Missing new password.
        change(emp, "{\"currentPassword\":\"password\"}")
                .andExpect(status().isBadRequest());
        // Same as the current one.
        change(emp, "{\"currentPassword\":\"password\",\"newPassword\":\"password\"}")
                .andExpect(status().isBadRequest());

        // Nothing changed: the original password still signs in.
        login(email, "password");
    }

    @Test
    void changePassword_success_newWorks_oldFails_sessionsRevoked() throws Exception {
        String slug = "s" + System.nanoTime();
        String owner = register(slug).get("accessToken").asText();
        String email = "pw-emp-" + slug + "@example.com";
        createEmp(owner, email, "EMPLOYEE");
        JsonNode s1 = session(email, "password");
        JsonNode s2 = session(email, "password");

        change(s1.get("accessToken").asText(), "{\"currentPassword\":\"password\",\"newPassword\":\"brand-new-pw\"}")
                .andExpect(status().isNoContent());

        login(email, "brand-new-pw");
        expectLoginFails(email, "password");
        // No refreshToken sent → every existing session's refresh token is revoked.
        refresh(s1.get("refreshToken").asText()).andExpect(status().isBadRequest());
        refresh(s2.get("refreshToken").asText()).andExpect(status().isBadRequest());
    }

    @Test
    void changePassword_withRefreshToken_keepsThatSessionOnly() throws Exception {
        String slug = "k" + System.nanoTime();
        String owner = register(slug).get("accessToken").asText();
        String email = "pw-emp-" + slug + "@example.com";
        createEmp(owner, email, "EMPLOYEE");
        JsonNode mine = session(email, "password");
        JsonNode other = session(email, "password");

        change(mine.get("accessToken").asText(),
                "{\"currentPassword\":\"password\",\"newPassword\":\"brand-new-pw\",\"refreshToken\":\"%s\"}"
                        .formatted(mine.get("refreshToken").asText()))
                .andExpect(status().isNoContent());

        refresh(other.get("refreshToken").asText()).andExpect(status().isBadRequest());
        refresh(mine.get("refreshToken").asText()).andExpect(status().isOk());
    }

    @Test
    void changePassword_cannotSpareSomeoneElsesSession() throws Exception {
        String slug = "x" + System.nanoTime();
        JsonNode ownerSession = register(slug);
        String email = "pw-emp-" + slug + "@example.com";
        createEmp(ownerSession.get("accessToken").asText(), email, "EMPLOYEE");
        JsonNode emp = session(email, "password");

        // Passing another user's refresh token spares nothing of the caller's and leaves theirs alone.
        change(emp.get("accessToken").asText(),
                "{\"currentPassword\":\"password\",\"newPassword\":\"brand-new-pw\",\"refreshToken\":\"%s\"}"
                        .formatted(ownerSession.get("refreshToken").asText()))
                .andExpect(status().isNoContent());
        refresh(emp.get("refreshToken").asText()).andExpect(status().isBadRequest());
        refresh(ownerSession.get("refreshToken").asText()).andExpect(status().isOk());
    }

    // ---------- reset-password ----------

    @Test
    void resetPassword_hrResetsEmployee_theyCanLogIn_andAreSignedOut() throws Exception {
        String slug = "h" + System.nanoTime();
        String owner = register(slug).get("accessToken").asText();
        String hrEmail = "pw-hr-" + slug + "@example.com";
        String empEmail = "pw-emp-" + slug + "@example.com";
        createEmp(owner, hrEmail, "HR_ADMIN");
        String empId = createEmp(owner, empEmail, "EMPLOYEE");
        String hr = login(hrEmail, "password");
        JsonNode empSession = session(empEmail, "password");

        reset(hr, empId, "temp-pass-123").andExpect(status().isNoContent());

        login(empEmail, "temp-pass-123");
        expectLoginFails(empEmail, "password");
        refresh(empSession.get("refreshToken").asText()).andExpect(status().isBadRequest());
        // The admin's own session is untouched.
        login(hrEmail, "password");
    }

    @Test
    void resetPassword_permissions() throws Exception {
        String slug = "p" + System.nanoTime();
        JsonNode reg = register(slug);
        String owner = reg.get("accessToken").asText();
        String ownerId = reg.get("employee").get("id").asText();
        String ownerEmail = "pw-owner-" + slug + "@example.com";
        String hrEmail = "pw-hr-" + slug + "@example.com";
        String mgrEmail = "pw-mgr-" + slug + "@example.com";
        String empEmail = "pw-emp-" + slug + "@example.com";
        String hrId = createEmp(owner, hrEmail, "HR_ADMIN");
        createEmp(owner, mgrEmail, "MANAGER");
        String empId = createEmp(owner, empEmail, "EMPLOYEE");
        String hr = login(hrEmail, "password");
        String mgr = login(mgrEmail, "password");
        String emp = login(empEmail, "password");

        // Unauthenticated → 401.
        mvc.perform(post("/api/employees/" + empId + "/reset-password").contentType("application/json")
                        .content("{\"newPassword\":\"temp-pass-123\"}"))
                .andExpect(status().isUnauthorized());
        // MANAGER / EMPLOYEE → 403 (even for their own account).
        reset(mgr, empId, "temp-pass-123").andExpect(status().isForbidden());
        reset(emp, empId, "temp-pass-123").andExpect(status().isForbidden());
        // HR_ADMIN may not reset the OWNER.
        reset(hr, ownerId, "temp-pass-123").andExpect(status().isForbidden());
        // Too short → 400.
        reset(hr, empId, "short").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.newPassword").exists());

        // None of the rejected calls changed anything.
        login(empEmail, "password");
        login(ownerEmail, "password");

        // The OWNER can reset an HR_ADMIN.
        reset(owner, hrId, "temp-pass-456").andExpect(status().isNoContent());
        login(hrEmail, "temp-pass-456");
    }

    @Test
    void resetPassword_crossTenant_404() throws Exception {
        String slugA = "a" + System.nanoTime();
        String ownerA = register(slugA).get("accessToken").asText();
        String empEmail = "pw-emp-" + slugA + "@example.com";
        String empId = createEmp(ownerA, empEmail, "EMPLOYEE");
        String ownerB = register("b" + System.nanoTime()).get("accessToken").asText();

        reset(ownerB, empId, "temp-pass-123").andExpect(status().isNotFound());
        login(empEmail, "password");
    }
}
