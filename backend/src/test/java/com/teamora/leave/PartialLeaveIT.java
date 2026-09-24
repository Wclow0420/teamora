package com.teamora.leave;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Partial-day leave: half days and hourly leave consume a fraction of a working day,
 * full-day ranges count only scheduled working days, and the validation rules return a
 * friendly 400.
 *
 * <p>Fixtures live in freshly-registered companies (Mon–Fri, 8h/day defaults) on 2027
 * dates, so nothing here can disturb the seeded demo data or any payroll period other
 * tests assert on. The one exception is the balance test, which needs an employee who
 * actually has a leave balance on record (only the demo seed creates those) — it asserts
 * a <em>delta</em> so it stays correct however many other tests touch Amir's leave.
 */
class PartialLeaveIT extends AbstractIntegrationTest {

    private static final String MON = "2027-03-01";      // Monday
    private static final String TUE = "2027-03-02";      // Tuesday
    private static final String FRI = "2027-03-05";      // Friday
    private static final String SAT = "2027-03-06";      // Saturday
    private static final String SUN = "2027-03-07";      // Sunday
    private static final String NEXT_MON = "2027-03-08"; // Monday

    // ---------- happy paths ----------

    @Test
    void halfDay_consumesHalfAWorkingDay() throws Exception {
        String emp = company("half");
        JsonNode res = applyOk(emp, "ANNUAL", """
                {"leaveTypeId":"%s","startDate":"%s","endDate":"%s","durationUnit":"HALF_DAY","halfDayPeriod":"AM","reason":"Clinic"}""");

        assertThat(res.get("days").decimalValue()).isEqualByComparingTo("0.50");
        assertThat(res.get("durationUnit").asText()).isEqualTo("HALF_DAY");
        assertThat(res.get("halfDayPeriod").asText()).isEqualTo("AM");
        assertThat(res.get("durationLabel").asText()).isEqualTo("Half day (AM)");
        assertThat(res.get("status").asText()).isEqualTo("PENDING");
    }

    @Test
    void twoHoursOfAnEightHourDay_isAQuarterDay() throws Exception {
        String emp = company("hours");
        JsonNode res = applyOk(emp, "ANNUAL", """
                {"leaveTypeId":"%s","startDate":"%s","endDate":"%s","durationUnit":"HOURS","hours":2,"startTime":"14:00","reason":"Errand"}""");

        assertThat(res.get("days").decimalValue()).isEqualByComparingTo("0.25");
        assertThat(res.get("durationUnit").asText()).isEqualTo("HOURS");
        assertThat(res.get("hoursLabel").asText()).isEqualTo("2 hours");
        assertThat(res.get("durationLabel").asText()).isEqualTo("2 hours");
    }

    @Test
    void fullDayAcrossAWeekend_countsOnlyWorkingDays() throws Exception {
        String emp = company("week");
        // Fri → Mon is four calendar days but only two Mon–Fri working days.
        JsonNode res = okBody(apply(emp, "ANNUAL", FRI, NEXT_MON,
                "{\"leaveTypeId\":\"%s\",\"startDate\":\"%s\",\"endDate\":\"%s\",\"reason\":\"Long weekend\"}"));

        assertThat(res.get("days").decimalValue()).isEqualByComparingTo("2.00");
        assertThat(res.get("durationUnit").asText()).isEqualTo("FULL_DAY");
        assertThat(res.get("durationLabel").asText()).isEqualTo("2 days");
    }

    @Test
    void approvingAHalfDay_addsHalfADayToTheBalance() throws Exception {
        // Amir is the only fixture with a real leave balance on record.
        String amir = login("amir@lumi.com", "password");
        String annual = leaveTypeId(amir, "ANNUAL");
        BigDecimal before = annualUsed(amir);

        var applied = mvc.perform(post("/api/leave/requests").header("Authorization", bearer(amir))
                        .contentType("application/json")
                        .content(("{\"leaveTypeId\":\"%s\",\"startDate\":\"2027-04-05\",\"endDate\":\"2027-04-05\","
                                + "\"durationUnit\":\"HALF_DAY\",\"halfDayPeriod\":\"PM\",\"reason\":\"School run\"}")
                                .formatted(annual)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode appliedBody = parse(applied.getResponse().getContentAsString());
        assertThat(appliedBody.get("days").decimalValue()).isEqualByComparingTo("0.50");
        String id = appliedBody.get("id").asText();

        String sarah = login("sarah@lumi.com", "password");
        mvc.perform(post("/api/admin/leave/requests/" + id + "/approve").header("Authorization", bearer(sarah)))
                .andExpect(status().isOk());

        assertThat(annualUsed(amir)).isEqualByComparingTo(before.add(new BigDecimal("0.5")));
    }

    // ---------- validation ----------

    @Test
    void halfDayAndHours_rejectADateRange() throws Exception {
        String emp = company("range");
        apply(emp, "ANNUAL", MON, TUE,
                "{\"leaveTypeId\":\"%s\",\"startDate\":\"%s\",\"endDate\":\"%s\",\"durationUnit\":\"HALF_DAY\",\"halfDayPeriod\":\"AM\"}")
                .andExpect(status().isBadRequest());
        apply(emp, "ANNUAL", MON, TUE,
                "{\"leaveTypeId\":\"%s\",\"startDate\":\"%s\",\"endDate\":\"%s\",\"durationUnit\":\"HOURS\",\"hours\":2}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void hours_mustBePositiveAndFitInsideAWorkingDay() throws Exception {
        String emp = company("hrsval");
        apply(emp, "ANNUAL", MON, MON,
                "{\"leaveTypeId\":\"%s\",\"startDate\":\"%s\",\"endDate\":\"%s\",\"durationUnit\":\"HOURS\",\"hours\":9}")
                .andExpect(status().isBadRequest());
        apply(emp, "ANNUAL", MON, MON,
                "{\"leaveTypeId\":\"%s\",\"startDate\":\"%s\",\"endDate\":\"%s\",\"durationUnit\":\"HOURS\",\"hours\":0}")
                .andExpect(status().isBadRequest());
        apply(emp, "ANNUAL", MON, MON,
                "{\"leaveTypeId\":\"%s\",\"startDate\":\"%s\",\"endDate\":\"%s\",\"durationUnit\":\"HOURS\"}")
                .andExpect(status().isBadRequest());
        // 8h of an 8h day is exactly a full day — allowed.
        JsonNode full = okBody(apply(emp, "ANNUAL", MON, MON,
                "{\"leaveTypeId\":\"%s\",\"startDate\":\"%s\",\"endDate\":\"%s\",\"durationUnit\":\"HOURS\",\"hours\":8}"));
        assertThat(full.get("days").decimalValue()).isEqualByComparingTo("1.00");
    }

    @Test
    void aRangeWithNoWorkingDays_isRejected() throws Exception {
        String emp = company("rest");
        apply(emp, "ANNUAL", SAT, SUN,
                "{\"leaveTypeId\":\"%s\",\"startDate\":\"%s\",\"endDate\":\"%s\",\"reason\":\"Weekend\"}")
                .andExpect(status().isBadRequest());
        // A half day on a rest day is rejected too.
        apply(emp, "ANNUAL", SAT, SAT,
                "{\"leaveTypeId\":\"%s\",\"startDate\":\"%s\",\"endDate\":\"%s\",\"durationUnit\":\"HALF_DAY\",\"halfDayPeriod\":\"AM\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void leaveOnAPublicHoliday_isRejected() throws Exception {
        String slug = "hol" + System.nanoTime();
        String owner = register(slug);
        String empEmail = "plemp-" + slug + "@example.com";
        createEmp(owner, "Emp " + slug, empEmail);
        String emp = login(empEmail, "password");

        // HR declares 12 Mar 2027 (a Friday) a public holiday…
        mvc.perform(post("/api/admin/calendar/events").header("Authorization", bearer(owner))
                        .contentType("application/json")
                        .content("{\"title\":\"Company holiday\",\"eventDate\":\"2027-03-12\",\"eventType\":\"HOLIDAY\"}"))
                .andExpect(status().isCreated());

        // … so nobody needs to spend leave on it, in any duration unit.
        apply(emp, "ANNUAL", "2027-03-12", "2027-03-12",
                "{\"leaveTypeId\":\"%s\",\"startDate\":\"%s\",\"endDate\":\"%s\",\"reason\":\"Rest\"}")
                .andExpect(status().isBadRequest());
        apply(emp, "ANNUAL", "2027-03-12", "2027-03-12",
                "{\"leaveTypeId\":\"%s\",\"startDate\":\"%s\",\"endDate\":\"%s\",\"durationUnit\":\"HALF_DAY\",\"halfDayPeriod\":\"AM\"}")
                .andExpect(status().isBadRequest());

        // A range that spans the holiday still works — it just doesn't charge for it.
        // Thu 11 → Fri 12 Mar 2027 → only Thursday counts.
        JsonNode spanning = okBody(apply(emp, "ANNUAL", "2027-03-11", "2027-03-12",
                "{\"leaveTypeId\":\"%s\",\"startDate\":\"%s\",\"endDate\":\"%s\",\"reason\":\"Break\"}"));
        assertThat(spanning.get("days").decimalValue()).isEqualByComparingTo("1.00");
    }

    @Test
    void endBeforeStart_isRejected() throws Exception {
        String emp = company("order");
        apply(emp, "ANNUAL", NEXT_MON, MON,
                "{\"leaveTypeId\":\"%s\",\"startDate\":\"%s\",\"endDate\":\"%s\"}")
                .andExpect(status().isBadRequest());
    }

    @Test
    void applying_requiresAuthentication() throws Exception {
        mvc.perform(post("/api/leave/requests").contentType("application/json")
                        .content("{\"leaveTypeId\":\"00000000-0000-0000-0000-000000000000\",\"startDate\":\"%s\",\"endDate\":\"%s\","
                                + "\"durationUnit\":\"HALF_DAY\",\"halfDayPeriod\":\"AM\"}".formatted(MON, MON)))
                .andExpect(status().isUnauthorized());
    }

    // ---- helpers ----

    /** A fresh company (Mon–Fri, 8h defaults) → the employee's access token. */
    private String company(String prefix) throws Exception {
        String slug = prefix + System.nanoTime();
        String owner = register(slug);
        String empEmail = "plemp-" + slug + "@example.com";
        createEmp(owner, "Emp " + slug, empEmail);
        return login(empEmail, "password");
    }

    private String register(String slug) throws Exception {
        String body = """
                {"companyName":"PL %s","fullName":"Owner %s","email":"plowner-%s@example.com","password":"password"}"""
                .formatted(slug, slug, slug);
        var res = mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn();
        return parse(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private void createEmp(String owner, String name, String email) throws Exception {
        String body = "{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"password\",\"role\":\"EMPLOYEE\"}"
                .formatted(name, email);
        mvc.perform(post("/api/employees").header("Authorization", bearer(owner))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated());
    }

    /** Apply with a body template taking (leaveTypeId, startDate, endDate). */
    private org.springframework.test.web.servlet.ResultActions apply(
            String token, String typeCode, String start, String end, String template) throws Exception {
        String body = template.formatted(leaveTypeId(token, typeCode), start, end);
        return mvc.perform(post("/api/leave/requests").header("Authorization", bearer(token))
                .contentType("application/json").content(body));
    }

    /** Apply on the single date {@link #MON} and expect 200. */
    private JsonNode applyOk(String token, String typeCode, String template) throws Exception {
        return okBody(apply(token, typeCode, MON, MON, template));
    }

    private JsonNode okBody(org.springframework.test.web.servlet.ResultActions actions) throws Exception {
        return parse(actions.andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private BigDecimal annualUsed(String token) throws Exception {
        var res = mvc.perform(get("/api/leave/balances").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        for (JsonNode b : parse(res.getResponse().getContentAsString())) {
            if ("ANNUAL".equals(b.get("code").asText())) {
                return b.get("used").decimalValue();
            }
        }
        throw new AssertionError("No ANNUAL balance on record");
    }

    private JsonNode parse(String json) {
        try {
            return om.readTree(json);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
