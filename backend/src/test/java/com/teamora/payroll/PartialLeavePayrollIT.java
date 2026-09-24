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
 * Partial-day leave priced by payroll, to the sen.
 *
 * <p>Runs in its own freshly-registered company (Mon–Fri, 8h/day defaults) for Feb 2026,
 * which has exactly 20 scheduled working days — so an RM4,000 salary derives to a clean
 * RM200.00/day and the fractions are exact:
 * <ul>
 *   <li>an unpaid <b>half day</b> costs RM100.00 (half the daily rate)</li>
 *   <li>an unpaid <b>2 hours</b> of an 8h day costs RM50.00 (a quarter)</li>
 *   <li><b>paid</b> partial leave costs nothing</li>
 *   <li>an unpaid full-day range over a weekend charges only the working days</li>
 * </ul>
 */
class PartialLeavePayrollIT extends AbstractIntegrationTest {

    private static final String PERIOD = "2026-02"; // 20 Mon–Fri days → RM200.00/day on RM4,000

    @Test
    void unpaidPartialLeaveReducesBasicByTheExactFraction() throws Exception {
        String slug = "pl" + System.nanoTime();
        JsonNode reg = register(slug);
        String owner = reg.get("accessToken").asText();

        String hanaEmail = "hana-" + slug + "@example.com";   // unpaid half day
        String imanEmail = "iman-" + slug + "@example.com";   // unpaid 2 hours
        String jayaEmail = "jaya-" + slug + "@example.com";   // paid half day
        String kiraEmail = "kira-" + slug + "@example.com";   // unpaid Fri→Mon (2 working days)
        createEmployee(owner, "Hana Half", hanaEmail);
        createEmployee(owner, "Iman Hours", imanEmail);
        createEmployee(owner, "Jaya Paid", jayaEmail);
        createEmployee(owner, "Kira Weekend", kiraEmail);

        String hana = login(hanaEmail, "password");
        String iman = login(imanEmail, "password");
        String jaya = login(jayaEmail, "password");
        String kira = login(kiraEmail, "password");

        // Mon 2 / Tue 3 / Wed 4 Feb 2026 are all weekdays; Fri 6 → Mon 9 spans a weekend.
        approve(owner, apply(hana, leaveTypeId(hana, "UNPAID"),
                "{\"leaveTypeId\":\"%s\",\"startDate\":\"2026-02-02\",\"endDate\":\"2026-02-02\",\"durationUnit\":\"HALF_DAY\",\"halfDayPeriod\":\"AM\",\"reason\":\"x\"}"));
        approve(owner, apply(iman, leaveTypeId(iman, "UNPAID"),
                "{\"leaveTypeId\":\"%s\",\"startDate\":\"2026-02-03\",\"endDate\":\"2026-02-03\",\"durationUnit\":\"HOURS\",\"hours\":2,\"reason\":\"x\"}"));
        approve(owner, apply(jaya, leaveTypeId(jaya, "ANNUAL"),
                "{\"leaveTypeId\":\"%s\",\"startDate\":\"2026-02-04\",\"endDate\":\"2026-02-04\",\"durationUnit\":\"HALF_DAY\",\"halfDayPeriod\":\"PM\",\"reason\":\"x\"}"));
        approve(owner, apply(kira, leaveTypeId(kira, "UNPAID"),
                "{\"leaveTypeId\":\"%s\",\"startDate\":\"2026-02-06\",\"endDate\":\"2026-02-09\",\"reason\":\"x\"}"));

        MvcResult run = mvc.perform(post("/api/admin/payroll/run")
                        .header("Authorization", bearer(owner))
                        .contentType("application/json").content("{\"period\":\"" + PERIOD + "\"}"))
                .andExpect(status().isOk())
                .andReturn();

        // RM4,000 − ½×RM200 = RM3,900.00 · − ¼×RM200 = RM3,950.00 · paid = RM4,000.00 · − 2×RM200 = RM3,600.00
        assertThat(lineBasic(run, "Hana Half")).isEqualTo("3,900.00");
        assertThat(lineBasic(run, "Iman Hours")).isEqualTo("3,950.00");
        assertThat(lineBasic(run, "Jaya Paid")).isEqualTo("4,000.00");
        assertThat(lineBasic(run, "Kira Weekend")).isEqualTo("3,600.00");

        // Payslip transparency figures carry the fraction, not a rounded whole day.
        JsonNode hanaSlip = payslip(hana);
        assertThat(hanaSlip.get("unpaidDays").decimalValue()).isEqualByComparingTo("0.50");
        assertThat(hanaSlip.get("unpaidDaysLabel").asText()).isEqualTo("0.5 days");
        assertThat(hanaSlip.get("unpaidDeductionLabel").asText()).isEqualTo("100.00");
        assertThat(hanaSlip.get("dailyRateLabel").asText()).isEqualTo("200.00");

        JsonNode imanSlip = payslip(iman);
        assertThat(imanSlip.get("unpaidDays").decimalValue()).isEqualByComparingTo("0.25");
        assertThat(imanSlip.get("unpaidDaysLabel").asText()).isEqualTo("0.25 days");
        assertThat(imanSlip.get("unpaidDeductionLabel").asText()).isEqualTo("50.00");

        JsonNode jayaSlip = payslip(jaya);
        assertThat(jayaSlip.get("unpaidDays").decimalValue()).isEqualByComparingTo("0");
        assertThat(jayaSlip.get("unpaidDaysLabel").asText()).isEqualTo("None");
        assertThat(jayaSlip.get("basicLabel").asText()).isEqualTo("4,000.00");

        JsonNode kiraSlip = payslip(kira);
        assertThat(kiraSlip.get("unpaidDays").decimalValue()).isEqualByComparingTo("2.00");
        assertThat(kiraSlip.get("unpaidDaysLabel").asText()).isEqualTo("2 days");
    }

    // ---- helpers ----

    private JsonNode register(String slug) throws Exception {
        String body = """
                {"companyName":"PLP %s","fullName":"Owner %s","email":"plpowner-%s@example.com","password":"password"}"""
                .formatted(slug, slug, slug);
        var res = mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(res.getResponse().getContentAsString());
    }

    private void createEmployee(String owner, String name, String email) throws Exception {
        String body = "{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"password\",\"role\":\"EMPLOYEE\",\"monthlySalary\":4000}"
                .formatted(name, email);
        mvc.perform(post("/api/employees").header("Authorization", bearer(owner))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated());
    }

    /** Apply with a body template taking the leave-type id; returns the new request id. */
    private String apply(String token, String leaveTypeId, String template) throws Exception {
        var res = mvc.perform(post("/api/leave/requests").header("Authorization", bearer(token))
                        .contentType("application/json").content(template.formatted(leaveTypeId)))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private void approve(String owner, String leaveId) throws Exception {
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
