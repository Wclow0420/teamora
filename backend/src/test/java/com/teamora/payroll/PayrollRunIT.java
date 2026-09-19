package com.teamora.payroll;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Covers the admin payroll-run engine: an HR_ADMIN can generate real payslips for a
 * period (correct figures from the calculator), drive the DRAFT → APPROVED → PAID
 * lifecycle, and re-run idempotently; MANAGER/EMPLOYEE are forbidden; unauthenticated
 * is 401. Uses a future period (no seeded payslips) so it can't disturb the seeded
 * June run other tests rely on.
 */
class PayrollRunIT extends AbstractIntegrationTest {

    private static final String PERIOD = "2026-07"; // no OT/claims seeded here → deterministic figures

    private String runBody(String period) {
        return "{\"period\":\"%s\"}".formatted(period);
    }

    @Test
    void hrAdmin_runsApprovesAndPaysPayroll() throws Exception {
        String sarah = login("sarah@lumi.com", "password");

        // Run: generates DRAFT payslips for all 10 active, salaried Lumi employees.
        MvcResult res = mvc.perform(post("/api/admin/payroll/run")
                        .header("Authorization", bearer(sarah))
                        .contentType("application/json").content(runBody(PERIOD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generated").value(true))
                .andExpect(jsonPath("$.employeeCount").value(10))
                .andExpect(jsonPath("$.skippedCount").value(0))
                .andExpect(jsonPath("$.status").value("DRAFT"))
                .andReturn();

        // Amir: RM4,000 basic, married + 2 children → PCB RM30; no OT/claims in July → net RM3,502.35.
        JsonNode amir = lineFor(res, "Amir Hakim");
        assertThat(amir.get("basicLabel").asText()).isEqualTo("4,000.00");
        assertThat(amir.get("epfLabel").asText()).isEqualTo("440.00");
        assertThat(amir.get("pcbLabel").asText()).isEqualTo("30.00");
        assertThat(amir.get("netLabel").asText()).isEqualTo("3,502.35");
        assertThat(amir.get("status").asText()).isEqualTo("DRAFT");

        // Re-run is idempotent — still one payslip per employee.
        mvc.perform(post("/api/admin/payroll/run")
                        .header("Authorization", bearer(sarah))
                        .contentType("application/json").content(runBody(PERIOD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeCount").value(10));

        // Can't mark paid before approval.
        mvc.perform(post("/api/admin/payroll/run/" + PERIOD + "/mark-paid")
                        .header("Authorization", bearer(sarah)))
                .andExpect(status().isBadRequest());

        // Approve → APPROVED.
        mvc.perform(post("/api/admin/payroll/run/" + PERIOD + "/approve")
                        .header("Authorization", bearer(sarah)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"));

        // Mark paid → PAID.
        mvc.perform(post("/api/admin/payroll/run/" + PERIOD + "/mark-paid")
                        .header("Authorization", bearer(sarah)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void runView_reportsNotGeneratedForUntouchedPeriod() throws Exception {
        String sarah = login("sarah@lumi.com", "password");
        mvc.perform(get("/api/admin/payroll/run").param("period", "2026-09")
                        .header("Authorization", bearer(sarah)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generated").value(false))
                .andExpect(jsonPath("$.employeeCount").value(0));
    }

    @Test
    void manager_isForbiddenFromRunningPayroll() throws Exception {
        String nadia = login("nadia@lumi.com", "password");
        mvc.perform(post("/api/admin/payroll/run")
                        .header("Authorization", bearer(nadia))
                        .contentType("application/json").content(runBody("2026-08")))
                .andExpect(status().isForbidden());
    }

    @Test
    void employee_isForbiddenFromRunningPayroll() throws Exception {
        String amir = login("amir@lumi.com", "password");
        mvc.perform(post("/api/admin/payroll/run")
                        .header("Authorization", bearer(amir))
                        .contentType("application/json").content(runBody("2026-08")))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_is401() throws Exception {
        mvc.perform(post("/api/admin/payroll/run")
                        .contentType("application/json").content(runBody("2026-08")))
                .andExpect(status().isUnauthorized());
    }

    private JsonNode lineFor(MvcResult res, String employeeName) throws Exception {
        JsonNode lines = om.readTree(res.getResponse().getContentAsString()).get("lines");
        for (JsonNode line : lines) {
            if (employeeName.equals(line.get("employeeName").asText())) {
                return line;
            }
        }
        throw new AssertionError("No payslip line for " + employeeName);
    }
}
