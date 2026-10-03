package com.teamora.audit;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Sensitive admin actions land in audit_events; GET /api/admin/audit is OWNER/HR_ADMIN only. */
class AuditTrailIT extends AbstractIntegrationTest {

    private record Co(String owner, String hr, String mgr, String emp, String empId, String empEmail) {}

    private String createEmp(String token, String email, String role, String extra) throws Exception {
        String body = "{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"password\",\"role\":\"%s\"%s}"
                .formatted(email, email, role, extra);
        var res = mvc.perform(post("/api/employees").header("Authorization", bearer(token))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn();
        return om.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private Co setup() throws Exception {
        String slug = "aud" + System.nanoTime();
        var reg = mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content("{\"companyName\":\"Audit %s\",\"fullName\":\"Olivia Owner\",\"email\":\"%s-o@example.com\",\"password\":\"password\"}"
                                .formatted(slug, slug)))
                .andExpect(status().isOk()).andReturn();
        String owner = om.readTree(reg.getResponse().getContentAsString()).get("accessToken").asText();
        createEmp(owner, slug + "-hr@example.com", "HR_ADMIN", "");
        createEmp(owner, slug + "-mgr@example.com", "MANAGER", "");
        String empEmail = slug + "-emp@example.com";
        String empId = createEmp(owner, empEmail, "EMPLOYEE",
                ",\"monthlySalary\":3000,\"bankName\":\"CIMB\",\"bankAccountNo\":\"8000111122\"");
        return new Co(owner, login(slug + "-hr@example.com", "password"), login(slug + "-mgr@example.com", "password"),
                login(empEmail, "password"), empId, empEmail);
    }

    private JsonNode audit(String token, String employeeId) throws Exception {
        var req = get("/api/admin/audit").header("Authorization", bearer(token));
        if (employeeId != null) req.param("employeeId", employeeId);
        return om.readTree(mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
    }

    private List<String> actions(JsonNode events) {
        List<String> out = new ArrayList<>();
        events.forEach(e -> out.add(e.get("action").asText()));
        return out;
    }

    private void patchEmp(String token, String id, String json) throws Exception {
        mvc.perform(patch("/api/employees/" + id).header("Authorization", bearer(token))
                        .contentType("application/json").content(json))
                .andExpect(status().isOk());
    }

    @Test
    void bankChangeByHr_isAudited_masked_andTheEmployeeIsNotified() throws Exception {
        Co co = setup();
        patchEmp(co.hr(), co.empId(), "{\"bankName\":\"Maybank\",\"bankAccountNo\":\"5140 1234 5678\"}");

        JsonNode events = audit(co.owner(), co.empId());
        JsonNode bank = events.get(0);
        assertThat(bank.get("action").asText()).isEqualTo("BANK_DETAILS_CHANGED");
        assertThat(bank.get("targetEmployeeId").asText()).isEqualTo(co.empId());
        assertThat(bank.get("actorName").asText()).contains("-hr@example.com");
        JsonNode d = bank.get("details");
        assertThat(d.get("bankNameFrom").asText()).isEqualTo("CIMB");
        assertThat(d.get("bankNameTo").asText()).isEqualTo("Maybank");
        assertThat(d.get("bankAccountFrom").asText()).isEqualTo("••••1122");
        assertThat(d.get("bankAccountTo").asText()).isEqualTo("••••5678");
        // The full account number is never written to the trail.
        assertThat(events.toString()).doesNotContain("51401234").doesNotContain("8000111122");

        // The employee hears about it in-app.
        JsonNode feed = om.readTree(mvc.perform(get("/api/notifications").header("Authorization", bearer(co.emp())))
                .andExpect(status().isOk()).andReturn().getResponse()
                .getContentAsString(java.nio.charset.StandardCharsets.UTF_8));
        assertThat(feed.toString()).contains("Your bank details were changed").contains("••••5678");
    }

    @Test
    void unchangedBankDetails_areNotAudited() throws Exception {
        Co co = setup();
        patchEmp(co.hr(), co.empId(), "{\"bankName\":\"CIMB\",\"bankAccountNo\":\"8000111122\",\"jobTitle\":\"Cook\"}");
        assertThat(actions(audit(co.hr(), co.empId()))).doesNotContain("BANK_DETAILS_CHANGED");
    }

    @Test
    void salaryRoleResetDeactivateReactivate_areAudited() throws Exception {
        Co co = setup();
        patchEmp(co.owner(), co.empId(), "{\"monthlySalary\":3500}");
        mvc.perform(patch("/api/employees/" + co.empId() + "/role").header("Authorization", bearer(co.owner()))
                        .contentType("application/json").content("{\"role\":\"MANAGER\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/employees/" + co.empId() + "/reset-password").header("Authorization", bearer(co.hr()))
                        .contentType("application/json").content("{\"newPassword\":\"temporary-pass-1\"}"))
                .andExpect(status().isNoContent());
        mvc.perform(post("/api/employees/" + co.empId() + "/deactivate").header("Authorization", bearer(co.hr())))
                .andExpect(status().isOk());
        mvc.perform(post("/api/employees/" + co.empId() + "/reactivate").header("Authorization", bearer(co.hr())))
                .andExpect(status().isOk());

        JsonNode events = audit(co.hr(), co.empId());
        assertThat(actions(events)).containsSubsequence(
                "EMPLOYEE_REACTIVATED", "EMPLOYEE_DEACTIVATED", "PASSWORD_RESET_BY_ADMIN", "ROLE_CHANGED", "SALARY_CHANGED");
        JsonNode salary = null;
        for (JsonNode e : events) if (e.get("action").asText().equals("SALARY_CHANGED")) salary = e;
        assertThat(salary.get("details").get("from").decimalValue()).isEqualByComparingTo("3000");
        assertThat(salary.get("details").get("to").decimalValue()).isEqualByComparingTo("3500");
        // The password itself is never recorded.
        assertThat(events.toString()).doesNotContain("temporary-pass-1");
    }

    @Test
    void payrollApproveAndMarkPaid_areAudited() throws Exception {
        Co co = setup();
        mvc.perform(post("/api/admin/payroll/run").header("Authorization", bearer(co.hr()))
                        .contentType("application/json").content("{\"period\":\"2026-08\"}"))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/payroll/run/2026-08/approve").header("Authorization", bearer(co.hr())))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/payroll/run/2026-08/mark-paid").header("Authorization", bearer(co.owner())))
                .andExpect(status().isOk());
        JsonNode events = audit(co.owner(), null);
        assertThat(actions(events)).containsSubsequence("PAYROLL_MARKED_PAID", "PAYROLL_APPROVED");
        assertThat(events.get(0).get("details").get("period").asText()).isEqualTo("2026-08");
        assertThat(events.get(0).get("targetEmployeeId").isNull()).isTrue();
    }

    @Test
    void auditEndpoint_rbac_andTenantScoping() throws Exception {
        Co co = setup();
        patchEmp(co.owner(), co.empId(), "{\"monthlySalary\":3100}");

        mvc.perform(get("/api/admin/audit").header("Authorization", bearer(co.mgr()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/audit").header("Authorization", bearer(co.emp()))).andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/audit")).andExpect(status().isUnauthorized());

        // Another company's HR sees none of it, and can't ask about this employee.
        Co other = setup();
        assertThat(audit(other.hr(), null).toString()).doesNotContain(co.empId());
        mvc.perform(get("/api/admin/audit").param("employeeId", co.empId()).header("Authorization", bearer(other.hr())))
                .andExpect(status().isNotFound());
    }
}
