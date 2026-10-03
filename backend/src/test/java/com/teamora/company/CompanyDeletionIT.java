package com.teamora.company;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Owner-only "delete company and all data" ({@code POST /api/companies/me/delete}).
 * Builds a company with a row in every tenant table (via the real API), deletes
 * it, and checks each table with SQL — plus that nobody can sign in afterwards
 * and a second company is untouched.
 */
class CompanyDeletionIT extends AbstractIntegrationTest {

    private static final double SITE_LAT = 3.139003;
    private static final double SITE_LNG = 101.686855;
    private static final String SMALL_JPEG_B64 = "/9j/2Q==";

    @Autowired
    JdbcTemplate jdbc;

    /**
     * Every table from the migrations (V1–V26) except flyway_schema_history,
     * with the SQL predicate that ties a row to a company. Keep in step with
     * {@link CompanyPurgeService}.
     */
    private static final Map<String, String> TENANT_TABLES = new LinkedHashMap<>();
    static {
        String emps = "(SELECT id FROM employees WHERE company_id = ?::uuid)";
        TENANT_TABLES.put("companies", "id = ?::uuid");
        TENANT_TABLES.put("company_settings", "company_id = ?::uuid");
        TENANT_TABLES.put("employees", "company_id = ?::uuid");
        TENANT_TABLES.put("refresh_tokens", "employee_id IN " + emps);
        TENANT_TABLES.put("password_reset_codes", "employee_id IN " + emps);
        TENANT_TABLES.put("push_tokens", "company_id = ?::uuid");
        TENANT_TABLES.put("notifications", "company_id = ?::uuid");
        TENANT_TABLES.put("attendance_records", "company_id = ?::uuid");
        TENANT_TABLES.put("attendance_photos", "company_id = ?::uuid");
        TENANT_TABLES.put("audit_events", "company_id = ?::uuid");
        TENANT_TABLES.put("leave_types", "company_id = ?::uuid");
        TENANT_TABLES.put("leave_balances", "company_id = ?::uuid");
        TENANT_TABLES.put("leave_requests", "company_id = ?::uuid");
        TENANT_TABLES.put("claims", "company_id = ?::uuid");
        TENANT_TABLES.put("overtime_requests", "company_id = ?::uuid");
        TENANT_TABLES.put("payslips", "company_id = ?::uuid");
        TENANT_TABLES.put("shifts", "company_id = ?::uuid");
        TENANT_TABLES.put("company_events", "company_id = ?::uuid");
        TENANT_TABLES.put("work_locations", "company_id = ?::uuid");
    }

    private record Co(String companyId, String name, String owner, String ownerEmail, String hr, String hrEmail,
                      String emp, String empEmail, List<String> employeeIds) {}

    private JsonNode json(ResultActions r) throws Exception {
        return om.readTree(r.andReturn().getResponse().getContentAsString());
    }

    private String createEmp(String owner, String email, String role, String extra) throws Exception {
        String body = "{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"password\",\"role\":\"%s\"%s}"
                .formatted(email, email, role, extra);
        return json(mvc.perform(post("/api/employees").header("Authorization", bearer(owner))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())).get("id").asText();
    }

    /** A company with at least one row in every tenant table. */
    private Co fullCompany(String slug) throws Exception {
        String name = "Purge Co " + slug;
        String ownerEmail = "del-owner-" + slug + "@example.com";
        JsonNode reg = json(mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content("{\"companyName\":\"%s\",\"fullName\":\"Owner %s\",\"email\":\"%s\",\"password\":\"password\"}"
                                .formatted(name, slug, ownerEmail)))
                .andExpect(status().isOk()));
        String owner = reg.get("accessToken").asText();
        String companyId = reg.get("employee").get("companyId").asText();
        String ownerId = reg.get("employee").get("id").asText();

        String hrEmail = "del-hr-" + slug + "@example.com";
        String mgrEmail = "del-mgr-" + slug + "@example.com";
        String empEmail = "del-emp-" + slug + "@example.com";
        String hrId = createEmp(owner, hrEmail, "HR_ADMIN", "");
        String mgrId = createEmp(owner, mgrEmail, "MANAGER", "");
        String empId = createEmp(owner, empEmail, "EMPLOYEE",
                ",\"monthlySalary\":3000,\"reportingManagerId\":\"%s\"".formatted(mgrId));

        // Work location, assigned to the employee (employees → work_locations FK).
        String siteId = json(mvc.perform(post("/api/admin/work-locations").header("Authorization", bearer(owner))
                        .contentType("application/json")
                        .content("{\"name\":\"HQ\",\"latitude\":%s,\"longitude\":%s,\"active\":true}".formatted(SITE_LAT, SITE_LNG)))
                .andExpect(status().isCreated())).get("id").asText();
        mvc.perform(patch("/api/employees/" + empId).header("Authorization", bearer(owner))
                        .contentType("application/json").content("{\"workLocationId\":\"" + siteId + "\"}"))
                .andExpect(status().isOk());

        String hr = login(hrEmail, "password");
        String emp = login(empEmail, "password");

        // Attendance with a clock-in selfie.
        mvc.perform(post("/api/attendance/clock-in").header("Authorization", bearer(emp)).contentType("application/json")
                        .content("{\"latitude\":%s,\"longitude\":%s,\"photoBase64\":\"%s\"}".formatted(SITE_LAT, SITE_LNG, SMALL_JPEG_B64)))
                .andExpect(status().isOk());
        // Leave request (→ approver notification).
        String annual = leaveTypeId(emp, "ANNUAL");
        mvc.perform(post("/api/leave/requests").header("Authorization", bearer(emp)).contentType("application/json")
                        .content("{\"leaveTypeId\":\"%s\",\"startDate\":\"2026-09-10\",\"endDate\":\"2026-09-11\",\"reason\":\"x\"}".formatted(annual)))
                .andExpect(status().isOk());
        // Claim with a receipt photo, approved by the owner so it's decided_by → employees.
        String claimId = json(mvc.perform(post("/api/claims").header("Authorization", bearer(emp)).contentType("application/json")
                        .content("{\"category\":\"MEAL\",\"title\":\"Lunch\",\"amount\":42.50,\"claimDate\":\"2026-09-15\",\"receiptBase64\":\"%s\"}"
                                .formatted(SMALL_JPEG_B64)))
                .andExpect(status().isOk())).get("id").asText();
        mvc.perform(post("/api/admin/claims/" + claimId + "/approve").header("Authorization", bearer(owner)))
                .andExpect(status().isOk());
        // Overtime.
        mvc.perform(post("/api/overtime").header("Authorization", bearer(emp)).contentType("application/json")
                        .content("{\"workDate\":\"2026-09-12\",\"hours\":2.5,\"reason\":\"late shift\"}"))
                .andExpect(status().isOk());
        // Shift.
        mvc.perform(post("/api/admin/schedule").header("Authorization", bearer(owner)).contentType("application/json")
                        .content("{\"employeeId\":\"%s\",\"date\":\"2026-09-15\",\"shiftType\":\"MORNING\"}".formatted(empId)))
                .andExpect(status().isOk());
        // Calendar event.
        mvc.perform(post("/api/admin/calendar/events").header("Authorization", bearer(hr)).contentType("application/json")
                        .content("{\"title\":\"Town hall\",\"eventDate\":\"2026-09-20\",\"eventType\":\"HOLIDAY\",\"timeLabel\":\"All day\"}"))
                .andExpect(status().isCreated());
        // Push token.
        mvc.perform(post("/api/notifications/push-token").header("Authorization", bearer(emp)).contentType("application/json")
                        .content("{\"token\":\"ExponentPushToken[del-%s]\",\"platform\":\"ios\"}".formatted(slug)))
                .andExpect(status().isNoContent());
        // Password reset code.
        mvc.perform(post("/api/auth/forgot-password").contentType("application/json")
                        .content("{\"email\":\"%s\"}".formatted(empEmail)))
                .andExpect(status().isOk());
        // Payslips.
        mvc.perform(post("/api/admin/payroll/run").header("Authorization", bearer(owner)).contentType("application/json")
                        .content("{\"period\":\"2026-09\"}"))
                .andExpect(status().isOk());
        // Audit trail (payroll approval is audited).
        mvc.perform(post("/api/admin/payroll/run/2026-09/approve").header("Authorization", bearer(owner)))
                .andExpect(status().isOk());

        return new Co(companyId, name, owner, ownerEmail, hr, hrEmail, emp, empEmail,
                List.of(ownerId, hrId, mgrId, empId));
    }

    private long count(String table, String companyId) {
        String where = TENANT_TABLES.get(table);
        Long n = jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE " + where, Long.class, companyId);
        return n == null ? 0 : n;
    }

    private Map<String, Long> counts(String companyId) {
        Map<String, Long> out = new LinkedHashMap<>();
        for (String table : TENANT_TABLES.keySet()) {
            out.put(table, count(table, companyId));
        }
        return out;
    }

    private ResultActions delete(String token, String password, String confirmName) throws Exception {
        var req = post("/api/companies/me/delete").contentType("application/json")
                .content(om.writeValueAsString(Map.of("password", password, "confirmName", confirmName)));
        if (token != null) req.header("Authorization", bearer(token));
        return mvc.perform(req);
    }

    @Test
    void tenantTableList_coversEveryTableInTheSchema() {
        List<String> tables = jdbc.queryForList(
                "SELECT table_name FROM information_schema.tables WHERE table_schema = current_schema() "
                        + "AND table_type = 'BASE TABLE' AND table_name <> 'flyway_schema_history'", String.class);
        // A new tenant table must be added to CompanyPurgeService and to TENANT_TABLES here.
        assertThat(TENANT_TABLES.keySet()).containsExactlyInAnyOrderElementsOf(tables);
    }

    @Test
    void ownerDeletesCompany_everyRowGone_nobodyCanSignIn_otherCompanyUntouched() throws Exception {
        String slug = "a" + System.nanoTime();
        Co co = fullCompany(slug);
        Co other = fullCompany("b" + System.nanoTime());

        // Precondition: the setup really put a row in every tenant table.
        counts(co.companyId()).forEach((table, n) ->
                assertThat(n).as("rows in %s before delete", table).isPositive());
        Map<String, Long> otherBefore = counts(other.companyId());

        delete(co.owner(), "password", "  " + co.name().toUpperCase() + " ").andExpect(status().isNoContent());

        counts(co.companyId()).forEach((table, n) ->
                assertThat(n).as("rows in %s after delete", table).isZero());
        // Nothing left keyed by the old employee ids either (cascaded / orphaned rows).
        for (String id : co.employeeIds()) {
            for (String table : List.of("refresh_tokens", "password_reset_codes", "push_tokens", "notifications",
                    "attendance_records", "leave_balances", "leave_requests", "claims", "overtime_requests",
                    "payslips", "shifts")) {
                assertThat(jdbc.queryForObject("SELECT count(*) FROM " + table + " WHERE employee_id = ?::uuid",
                        Long.class, id)).as("%s rows for employee %s", table, id).isZero();
            }
        }

        // Nobody from the deleted company can sign in; outstanding access tokens stop working.
        for (String email : List.of(co.ownerEmail(), co.hrEmail(), co.empEmail())) {
            mvc.perform(post("/api/auth/login").contentType("application/json")
                            .content("{\"email\":\"%s\",\"password\":\"password\"}".formatted(email)))
                    .andExpect(status().isUnauthorized());
        }
        mvc.perform(get("/api/employees/me").header("Authorization", bearer(co.emp())))
                .andExpect(status().isUnauthorized());

        // The other company is untouched and still works.
        assertThat(counts(other.companyId())).isEqualTo(otherBefore);
        login(other.ownerEmail(), "password");
        mvc.perform(get("/api/employees/me").header("Authorization", bearer(other.emp())))
                .andExpect(status().isOk());
    }

    @Test
    void wrongPasswordOrName_400_andNothingDeleted() throws Exception {
        Co co = fullCompany("w" + System.nanoTime());
        Map<String, Long> before = counts(co.companyId());

        delete(co.owner(), "not-my-password", co.name())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Password is incorrect"));
        delete(co.owner(), "", co.name())
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Password is incorrect"));
        delete(co.owner(), "password", co.name() + " Sdn Bhd")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Type the company name exactly to confirm"));
        delete(co.owner(), "password", "")
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Type the company name exactly to confirm"));
        mvc.perform(post("/api/companies/me/delete").header("Authorization", bearer(co.owner())))
                .andExpect(status().isBadRequest());

        assertThat(counts(co.companyId())).isEqualTo(before);
        login(co.ownerEmail(), "password");
    }

    @Test
    void onlyTheOwner_hrAndEmployee403_unauthenticated401() throws Exception {
        Co co = fullCompany("p" + System.nanoTime());
        Map<String, Long> before = counts(co.companyId());

        delete(co.hr(), "password", co.name()).andExpect(status().isForbidden());
        delete(co.emp(), "password", co.name()).andExpect(status().isForbidden());
        delete(null, "password", co.name()).andExpect(status().isUnauthorized());

        assertThat(counts(co.companyId())).isEqualTo(before);
    }
}
