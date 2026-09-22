package com.teamora.payroll.export;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Statutory reporting & export: an HR_ADMIN runs payroll for a period, then reads the
 * contribution summary (totals == sum of rows, to the sen) and each of the four export
 * files (non-empty content, correct filename/mimeType/header). Covers the RBAC + validation
 * outcomes (no-run → 400, MANAGER → 403, unauthenticated → 401). Uses a future period so it
 * never disturbs the seeded June run or other test classes.
 */
class PayrollExportIT extends AbstractIntegrationTest {

    private static final String PERIOD = "2026-11";

    private String runBody(String period) {
        return "{\"period\":\"%s\"}".formatted(period);
    }

    /** Set Amir's statutory & bank identity so the exports carry real values, then run payroll. */
    private String setupRun(String hr) throws Exception {
        String amirId = idByName(hr, "Amir");
        mvc.perform(patch("/api/employees/" + amirId).header("Authorization", bearer(hr))
                        .contentType("application/json")
                        .content("""
                                {"nric":"900101-14-5678","epfNo":"EPF12345","socsoNo":"SOC12345",
                                 "taxNo":"SG12345678","bankName":"Maybank","bankAccountNo":"1234567890"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nric").value("900101-14-5678"))
                .andExpect(jsonPath("$.taxNo").value("SG12345678"))
                .andExpect(jsonPath("$.bankName").value("Maybank"));

        mvc.perform(post("/api/admin/payroll/run").header("Authorization", bearer(hr))
                        .contentType("application/json").content(runBody(PERIOD)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.generated").value(true));
        return amirId;
    }

    private String idByName(String token, String q) throws Exception {
        var res = mvc.perform(get("/api/employees").param("q", q).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return om.readTree(res.getResponse().getContentAsString()).get(0).get("id").asText();
    }

    @Test
    void summary_totalsEqualSumOfRows_andCarryIdentity() throws Exception {
        String sarah = login("sarah@lumi.com", "password");
        setupRun(sarah);

        MvcResult res = mvc.perform(get("/api/admin/payroll/export/summary").param("period", PERIOD)
                        .header("Authorization", bearer(sarah)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.period").value(PERIOD))
                .andReturn();

        JsonNode body = om.readTree(res.getResponse().getContentAsString());
        assertThat(body.get("generatedAtLabel").asText()).isNotBlank();
        JsonNode rows = body.get("rows");
        assertThat(rows).isNotEmpty();

        // Amir's row carries the identity we set.
        JsonNode amir = rowFor(rows, "Amir Hakim");
        assertThat(amir.get("nric").asText()).isEqualTo("900101-14-5678");
        assertThat(amir.get("epfNo").asText()).isEqualTo("EPF12345");
        assertThat(amir.get("taxNo").asText()).isEqualTo("SG12345678");

        // Totals == sum of the rows, to the sen, for every statutory column.
        String[] cols = {"epfEmployeeLabel", "epfEmployerLabel", "socsoEmployeeLabel", "socsoEmployerLabel",
                "eisEmployeeLabel", "eisEmployerLabel", "pcbLabel", "netLabel"};
        JsonNode totals = body.get("totals");
        for (String col : cols) {
            BigDecimal sum = BigDecimal.ZERO;
            for (JsonNode row : rows) {
                sum = sum.add(money(row.get(col).asText()));
            }
            assertThat(sum)
                    .as("total %s equals sum of rows", col)
                    .isEqualByComparingTo(money(totals.get(col).asText()));
        }
    }

    @Test
    void fileExports_returnContentFilenameMimeAndHeader() throws Exception {
        String sarah = login("sarah@lumi.com", "password");
        setupRun(sarah);

        JsonNode contributions = file(sarah, "contributions");
        assertThat(contributions.get("filename").asText()).isEqualTo("contributions-" + PERIOD + ".csv");
        assertThat(contributions.get("mimeType").asText()).isEqualTo("text/csv");
        assertThat(contributions.get("content").asText())
                .startsWith("Employee,Staff ID,NRIC,EPF No,EPF Employee,EPF Employer,"
                        + "SOCSO No,SOCSO Employee,SOCSO Employer,EIS Employee,EIS Employer,Tax No,PCB,Net Pay")
                .contains("TOTAL")
                .contains("SG12345678"); // Amir's tax no in a data row

        JsonNode bank = file(sarah, "bank");
        assertThat(bank.get("filename").asText()).isEqualTo("bank-" + PERIOD + ".csv");
        assertThat(bank.get("mimeType").asText()).isEqualTo("text/csv");
        assertThat(bank.get("content").asText())
                .startsWith("Employee,Staff ID,Bank Name,Bank Account No,Net Pay")
                .contains("Maybank")
                .contains("1234567890");

        JsonNode payroll = file(sarah, "payroll");
        assertThat(payroll.get("filename").asText()).isEqualTo("payroll-" + PERIOD + ".csv");
        assertThat(payroll.get("mimeType").asText()).isEqualTo("text/csv");
        assertThat(payroll.get("content").asText())
                .startsWith("Employee,Staff ID,Basic,Overtime,Claims,Bonus,Gross,"
                        + "EPF,SOCSO,EIS,PCB,Deductions,Net,Unpaid Days,Unpaid Deduction");

        JsonNode cp39 = file(sarah, "cp39");
        assertThat(cp39.get("filename").asText()).isEqualTo("cp39-" + PERIOD + ".txt");
        assertThat(cp39.get("mimeType").asText()).isEqualTo("text/plain");
        assertThat(cp39.get("content").asText())
                .contains(Cp39Generator.LAYOUT_VERSION)
                .contains("AMIR HAKIM");
    }

    @Test
    void unknownFileType_is400() throws Exception {
        String sarah = login("sarah@lumi.com", "password");
        setupRun(sarah);
        mvc.perform(get("/api/admin/payroll/export/file").param("period", PERIOD).param("type", "bogus")
                        .header("Authorization", bearer(sarah)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void noRunForPeriod_is400() throws Exception {
        String sarah = login("sarah@lumi.com", "password");
        mvc.perform(get("/api/admin/payroll/export/summary").param("period", "2027-12")
                        .header("Authorization", bearer(sarah)))
                .andExpect(status().isBadRequest());
        mvc.perform(get("/api/admin/payroll/export/file").param("period", "2027-12").param("type", "contributions")
                        .header("Authorization", bearer(sarah)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void manager_isForbidden() throws Exception {
        String nadia = login("nadia@lumi.com", "password");
        mvc.perform(get("/api/admin/payroll/export/summary").param("period", PERIOD)
                        .header("Authorization", bearer(nadia)))
                .andExpect(status().isForbidden());
        mvc.perform(get("/api/admin/payroll/export/file").param("period", PERIOD).param("type", "contributions")
                        .header("Authorization", bearer(nadia)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_is401() throws Exception {
        mvc.perform(get("/api/admin/payroll/export/summary").param("period", PERIOD))
                .andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/payroll/export/file").param("period", PERIOD).param("type", "contributions"))
                .andExpect(status().isUnauthorized());
    }

    // ---- helpers ----

    private JsonNode file(String token, String type) throws Exception {
        var res = mvc.perform(get("/api/admin/payroll/export/file").param("period", PERIOD).param("type", type)
                        .header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        return om.readTree(res.getResponse().getContentAsString());
    }

    private JsonNode rowFor(JsonNode rows, String name) {
        for (JsonNode r : rows) {
            if (name.equals(r.get("employeeName").asText())) {
                return r;
            }
        }
        throw new AssertionError("No summary row for " + name);
    }

    /** Parse a grouped money label ("4,000.00") into a BigDecimal. */
    private static BigDecimal money(String label) {
        return new BigDecimal(label.replace(",", ""));
    }
}
