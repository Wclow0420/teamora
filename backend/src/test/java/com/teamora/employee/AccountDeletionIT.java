package com.teamora.employee;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Duration;
import java.time.Instant;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Employee "delete my account" request ({@code POST /api/employees/me/deletion-request})
 * and the admin side that acts on it ({@code POST /api/employees/{id}/deactivate|reactivate}).
 * Every scenario registers its own company so the seeded demo accounts stay intact.
 */
class AccountDeletionIT extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    private record Co(String owner, String ownerId, String hr, String hrId, String hrEmail,
                      String mgr, String mgrId, String emp, String empId, String empEmail, JsonNode empSession) {}

    private JsonNode json(ResultActions r) throws Exception {
        return om.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private String createEmp(String owner, String email, String role, String extra) throws Exception {
        String body = "{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"password\",\"role\":\"%s\"%s}"
                .formatted(email.substring(0, email.indexOf('@')), email, role, extra);
        return json(mvc.perform(post("/api/employees").header("Authorization", bearer(owner))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())).get("id").asText();
    }

    private JsonNode session(String email) throws Exception {
        return json(mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"%s\",\"password\":\"password\"}".formatted(email)))
                .andExpect(status().isOk()));
    }

    private Co company(String slug) throws Exception {
        JsonNode reg = json(mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content("{\"companyName\":\"Del %s\",\"fullName\":\"Owner %s\",\"email\":\"ad-owner-%s@example.com\",\"password\":\"password\"}"
                                .formatted(slug, slug, slug)))
                .andExpect(status().isOk()));
        String owner = reg.get("accessToken").asText();
        String hrEmail = "ad-hr-" + slug + "@example.com";
        String mgrEmail = "ad-mgr-" + slug + "@example.com";
        String empEmail = "ad-emp-" + slug + "@example.com";
        String hrId = createEmp(owner, hrEmail, "HR_ADMIN", "");
        String mgrId = createEmp(owner, mgrEmail, "MANAGER", "");
        String empId = createEmp(owner, empEmail, "EMPLOYEE", ",\"reportingManagerId\":\"%s\"".formatted(mgrId));
        JsonNode empSession = session(empEmail);
        return new Co(owner, reg.get("employee").get("id").asText(), login(hrEmail, "password"), hrId, hrEmail,
                login(mgrEmail, "password"), mgrId, empSession.get("accessToken").asText(), empId, empEmail, empSession);
    }

    private ResultActions request(String token, String body) throws Exception {
        var req = post("/api/employees/me/deletion-request");
        if (token != null) req.header("Authorization", bearer(token));
        if (body != null) req.contentType("application/json").content(body);
        return mvc.perform(req);
    }

    private ResultActions deactivate(String token, String id) throws Exception {
        var req = post("/api/employees/" + id + "/deactivate");
        if (token != null) req.header("Authorization", bearer(token));
        return mvc.perform(req);
    }

    private ResultActions reactivate(String token, String id) throws Exception {
        var req = post("/api/employees/" + id + "/reactivate");
        if (token != null) req.header("Authorization", bearer(token));
        return mvc.perform(req);
    }

    private ResultActions refresh(String refreshToken) throws Exception {
        return mvc.perform(post("/api/auth/refresh").contentType("application/json")
                .content("{\"refreshToken\":\"%s\"}".formatted(refreshToken)));
    }

    private ResultActions loginAttempt(String email) throws Exception {
        return mvc.perform(post("/api/auth/login").contentType("application/json")
                .content("{\"email\":\"%s\",\"password\":\"password\"}".formatted(email)));
    }

    private long deletionNotifications(String employeeId) {
        return jdbc.queryForObject("SELECT count(*) FROM notifications WHERE employee_id = ?::uuid "
                + "AND type = 'ACCOUNT_DELETION_REQUEST'", Long.class, employeeId);
    }

    // ---------- deletion request ----------

    @Test
    void employeeRequest_notifiesOwnerAndHr_once() throws Exception {
        Co co = company("r" + System.nanoTime());
        Instant before = Instant.now().minusSeconds(5);

        JsonNode res = json(request(co.emp(), "{\"reason\":\"I've left the company.\"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedAt").isString()));
        Instant requestedAt = Instant.parse(res.get("requestedAt").asText());
        assertThat(requestedAt).isAfter(before);

        assertThat(deletionNotifications(co.ownerId())).isEqualTo(1);
        assertThat(deletionNotifications(co.hrId())).isEqualTo(1);
        assertThat(deletionNotifications(co.mgrId())).isZero();   // managers aren't told
        mvc.perform(get("/api/notifications").header("Authorization", bearer(co.hr())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.today[0].title").value("Account deletion request"))
                .andExpect(jsonPath("$.today[0].body").value(
                        co.empEmail().substring(0, co.empEmail().indexOf('@'))
                                + " asked for their Teamora account to be deleted. I've left the company."));

        // A repeat within 24h: 200 with the original time, no new notifications.
        request(co.emp(), null)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requestedAt").value(res.get("requestedAt").asText()));
        assertThat(deletionNotifications(co.ownerId())).isEqualTo(1);
        assertThat(deletionNotifications(co.hrId())).isEqualTo(1);

        // Once the 24h window has passed, a new request notifies again.
        jdbc.update("UPDATE employees SET deletion_requested_at = ? WHERE id = ?::uuid",
                java.sql.Timestamp.from(Instant.now().minus(Duration.ofHours(25))), co.empId());
        request(co.emp(), "{}").andExpect(status().isOk());
        assertThat(deletionNotifications(co.ownerId())).isEqualTo(2);
        assertThat(deletionNotifications(co.hrId())).isEqualTo(2);
    }

    @Test
    void hrAdminRequest_notifiesOwnerNotThemselves_managerCanRequest() throws Exception {
        Co co = company("h" + System.nanoTime());
        request(co.hr(), null).andExpect(status().isOk());
        assertThat(deletionNotifications(co.ownerId())).isEqualTo(1);
        assertThat(deletionNotifications(co.hrId())).isZero();

        request(co.mgr(), "{\"reason\":\"\"}").andExpect(status().isOk());
        assertThat(deletionNotifications(co.ownerId())).isEqualTo(2);
        assertThat(deletionNotifications(co.hrId())).isEqualTo(1);
    }

    @Test
    void deletionRequest_ownerRejected_validation_unauthenticated() throws Exception {
        Co co = company("o" + System.nanoTime());
        request(co.owner(), null)
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("As the owner, delete the company from Company settings instead."));
        request(co.emp(), "{\"reason\":\"" + "x".repeat(301) + "\"}")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.reason").exists());
        request(null, null).andExpect(status().isUnauthorized());
        assertThat(deletionNotifications(co.ownerId())).isZero();
    }

    // ---------- deactivate / reactivate ----------

    @Test
    void deactivate_locksOut_keepsHistory_reactivateRestores() throws Exception {
        Co co = company("d" + System.nanoTime());
        String refreshToken = co.empSession().get("refreshToken").asText();
        mvc.perform(post("/api/notifications/push-token").header("Authorization", bearer(co.emp()))
                        .contentType("application/json").content("{\"token\":\"ExponentPushToken[ad-%s]\",\"platform\":\"ios\"}"
                                .formatted(co.empId())))
                .andExpect(status().isNoContent());
        // Some history that must survive deactivation.
        mvc.perform(post("/api/overtime").header("Authorization", bearer(co.emp())).contentType("application/json")
                        .content("{\"workDate\":\"2026-09-12\",\"hours\":2,\"reason\":\"stocktake\"}"))
                .andExpect(status().isOk());

        deactivate(co.hr(), co.empId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(co.empId()))
                .andExpect(jsonPath("$.active").value(false));

        // Locked out: the access token minted before deactivation, refresh, and login all 401.
        mvc.perform(get("/api/employees/me").header("Authorization", bearer(co.emp())))
                .andExpect(status().isUnauthorized());
        refresh(refreshToken).andExpect(status().isUnauthorized());
        loginAttempt(co.empEmail()).andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.message").value("Invalid email or password"));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM refresh_tokens WHERE employee_id = ?::uuid AND NOT revoked",
                Long.class, co.empId())).isZero();
        assertThat(jdbc.queryForObject("SELECT count(*) FROM push_tokens WHERE employee_id = ?::uuid",
                Long.class, co.empId())).isZero();

        // Their records stay; they still show in the directory, flagged inactive.
        assertThat(jdbc.queryForObject("SELECT count(*) FROM overtime_requests WHERE employee_id = ?::uuid",
                Long.class, co.empId())).isEqualTo(1);
        mvc.perform(get("/api/employees").header("Authorization", bearer(co.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + co.empId() + "')].active").value(org.hamcrest.Matchers.contains(false)));
        // Idempotent.
        deactivate(co.owner(), co.empId()).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(false));

        // Reactivate → they can sign in again.
        reactivate(co.owner(), co.empId())
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(true));
        String fresh = login(co.empEmail(), "password");
        mvc.perform(get("/api/employees/me").header("Authorization", bearer(fresh))).andExpect(status().isOk());
        reactivate(co.hr(), co.empId()).andExpect(status().isOk()).andExpect(jsonPath("$.active").value(true));
    }

    @Test
    void deactivatingAManager_movesTheirReportsToTheDefaultApprover() throws Exception {
        Co co = company("m" + System.nanoTime());
        deactivate(co.owner(), co.mgrId()).andExpect(status().isOk());
        mvc.perform(get("/api/employees/" + co.empId()).header("Authorization", bearer(co.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportingManagerId").doesNotExist());
    }

    @Test
    void deactivate_permissionsAndGuards() throws Exception {
        Co co = company("g" + System.nanoTime());
        Co other = company("x" + System.nanoTime());

        // Unauthenticated 401; MANAGER / EMPLOYEE 403.
        deactivate(null, co.empId()).andExpect(status().isUnauthorized());
        reactivate(null, co.empId()).andExpect(status().isUnauthorized());
        deactivate(co.mgr(), co.empId()).andExpect(status().isForbidden());
        deactivate(co.emp(), co.empId()).andExpect(status().isForbidden());
        reactivate(co.mgr(), co.empId()).andExpect(status().isForbidden());
        reactivate(co.emp(), co.empId()).andExpect(status().isForbidden());
        // Not yourself, not the owner.
        deactivate(co.hr(), co.hrId()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("your own account")));
        deactivate(co.hr(), co.ownerId()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("owner")));
        deactivate(co.owner(), co.ownerId()).andExpect(status().isBadRequest());
        // Another company's employee → 404.
        deactivate(other.owner(), co.empId()).andExpect(status().isNotFound());
        reactivate(other.owner(), co.empId()).andExpect(status().isNotFound());

        // None of that locked anyone out.
        login(co.empEmail(), "password");
        login(co.hrEmail(), "password");
    }
}
