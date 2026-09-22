package com.teamora.payroll;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Unpaid leave reduces the payroll basic at the derived daily rate; paid leave does not.
 * Builds its own isolated company (Mon–Fri default schedule) and runs payroll for Feb 2026,
 * which has exactly 20 scheduled working days → RM4,000 salary derives to RM200/day.
 */
class CompLeavePayrollIT extends AbstractIntegrationTest {

    private static final String PERIOD = "2026-02"; // 20 Mon–Fri days

    @Test
    void unpaidLeaveReducesBasic_paidLeaveDoesNot() throws Exception {
        String slug = "cl" + System.nanoTime();
        JsonNode reg = register(slug);
        String owner = reg.get("accessToken").asText();

        // Two RM4,000 employees, both approved by the owner (no reporting manager).
        String umaEmail = "uma-" + slug + "@example.com";
        String vinaEmail = "vina-" + slug + "@example.com";
        createEmployee(owner, "Uma Unpaid", umaEmail, "4000");
        createEmployee(owner, "Vina Paid", vinaEmail, "4000");

        String uma = login(umaEmail, "password");
        String vina = login(vinaEmail, "password");

        // Uma takes 2 unpaid weekdays (Mon 2 + Tue 3 Feb 2026); Vina takes 3 paid annual days.
        approveLeave(owner, applyLeave(uma, leaveTypeId(uma, "UNPAID"), "2026-02-02", "2026-02-03"));
        approveLeave(owner, applyLeave(vina, leaveTypeId(vina, "ANNUAL"), "2026-02-04", "2026-02-06"));

        // Run payroll.
        MvcResult run = mvc.perform(post("/api/admin/payroll/run")
                        .header("Authorization", bearer(owner))
                        .contentType("application/json").content("{\"period\":\"" + PERIOD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        // Uma: RM4,000 − 2×RM200 = RM3,600.00 basic. Vina: full RM4,000.00 (paid leave).
        assertThat(lineBasic(run, "Uma Unpaid")).isEqualTo("3,600.00");
        assertThat(lineBasic(run, "Vina Paid")).isEqualTo("4,000.00");

        // The payslip carries the transparency figures for Uma.
        JsonNode umaSlip = payslip(uma);
        assertThat(umaSlip.get("unpaidDays").asInt()).isEqualTo(2);
        assertThat(umaSlip.get("unpaidDeductionLabel").asText()).isEqualTo("400.00");
        assertThat(umaSlip.get("dailyRateLabel").asText()).isEqualTo("200.00");
        assertThat(umaSlip.get("basicLabel").asText()).isEqualTo("3,600.00");

        JsonNode vinaSlip = payslip(vina);
        assertThat(vinaSlip.get("unpaidDays").asInt()).isZero();
        assertThat(vinaSlip.get("basicLabel").asText()).isEqualTo("4,000.00");
    }

    // ---- helpers ----

    private JsonNode register(String slug) throws Exception {
        String body = """
                {"companyName":"Comp %s","fullName":"Owner %s","email":"owner-%s@example.com","password":"password"}"""
                .formatted(slug, slug, slug);
        var res = mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(res.getResponse().getContentAsString());
    }

    private void createEmployee(String owner, String name, String email, String salary) throws Exception {
        String body = "{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"password\",\"role\":\"EMPLOYEE\",\"monthlySalary\":%s}"
                .formatted(name, email, salary);
        mvc.perform(post("/api/employees").header("Authorization", bearer(owner))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated());
    }

    private String applyLeave(String token, String leaveTypeId, String start, String end) throws Exception {
        String body = "{\"leaveTypeId\":\"%s\",\"startDate\":\"%s\",\"endDate\":\"%s\",\"reason\":\"x\"}"
                .formatted(leaveTypeId, start, end);
        var res = mvc.perform(post("/api/leave/requests").header("Authorization", bearer(token))
                        .contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private void approveLeave(String owner, String leaveId) throws Exception {
        mvc.perform(post("/api/admin/leave/requests/" + leaveId + "/approve").header("Authorization", bearer(owner)))
                .andExpect(status().isOk());
    }

    private String lineBasic(MvcResult run, String employeeName) throws Exception {
        JsonNode lines = om.readTree(run.getResponse().getContentAsString()).get("lines");
        for (JsonNode line : lines) {
            if (employeeName.equals(line.get("employeeName").asText())) {
                return line.get("basicLabel").asText();
            }
        }
        throw new AssertionError("No payslip line for " + employeeName);
    }

    private JsonNode payslip(String token) throws Exception {
        var res = mvc.perform(get("/api/payroll/payslips/" + PERIOD).header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(res.getResponse().getContentAsString());
    }
}
