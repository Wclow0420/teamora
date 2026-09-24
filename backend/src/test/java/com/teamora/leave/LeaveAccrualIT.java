package com.teamora.leave;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Leave accrual, join-date proration and carry-forward.
 *
 * <p>Every fixture lives in its own freshly-registered company so nothing here can
 * disturb the seeded demo data (or the pinned payroll figures). Assertions never
 * hard-code today's date: where a test needs a known point inside the leave year it
 * moves the company's {@code leaveYearStartMonth} instead, which makes the elapsed-month
 * count exact whatever day the suite runs on.
 */
class LeaveAccrualIT extends AbstractIntegrationTest {

    // ---------- accrual modes ----------

    @Test
    void fixedAnnual_isFullyAccruedFromDayOne() throws Exception {
        Fixture f = fixture("fixed");
        JsonNode annual = balance(f.employeeToken, null, "ANNUAL");

        assertThat(annual.get("entitled").decimalValue()).isEqualByComparingTo("16.00");
        assertThat(annual.get("accruedToDate").decimalValue()).isEqualByComparingTo("16.00");
        assertThat(annual.get("carriedForward").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(annual.get("remaining").decimalValue()).isEqualByComparingTo("16.00");
        assertThat(annual.get("leaveYear").asInt()).isEqualTo(currentLeaveYear(f.ownerToken));
    }

    @Test
    void monthlyAccrual_accruesOneTwelfthPerElapsedMonth() throws Exception {
        Fixture f = fixture("monthly");
        // Put "today" in month 1 of the leave year → exactly 1/12 accrued.
        setLeaveYearStartMonth(f.ownerToken, monthsBeforeThisMonth(0));
        createType(f.ownerToken, "Study Leave", "STUDY1", 12, "MONTHLY_ACCRUAL", null);

        JsonNode study = balance(f.employeeToken, null, "STUDY1");
        assertThat(study.get("entitled").decimalValue()).isEqualByComparingTo("12.00");
        assertThat(study.get("accruedToDate").decimalValue()).isEqualByComparingTo("1.00");
        assertThat(study.get("remaining").decimalValue()).isEqualByComparingTo("1.00");

        // Shift the year start back a month → "today" is now in month 3 → 3/12.
        Fixture g = fixture("monthly3");
        setLeaveYearStartMonth(g.ownerToken, monthsBeforeThisMonth(2));
        createType(g.ownerToken, "Study Leave", "STUDY3", 12, "MONTHLY_ACCRUAL", null);

        JsonNode third = balance(g.employeeToken, null, "STUDY3");
        assertThat(third.get("accruedToDate").decimalValue()).isEqualByComparingTo("3.00");
    }

    @Test
    void noneAccrual_accruesNothingEvenWithAnEntitlement() throws Exception {
        Fixture f = fixture("none");
        createType(f.ownerToken, "Sabbatical", "SABB", 10, "NONE", null);

        JsonNode sabb = balance(f.employeeToken, null, "SABB");
        assertThat(sabb.get("entitled").decimalValue()).isEqualByComparingTo("10.00");
        assertThat(sabb.get("accruedToDate").decimalValue()).isEqualByComparingTo("0.00");
        assertThat(sabb.get("remaining").decimalValue()).isEqualByComparingTo("0.00");

        // The seeded Unpaid Leave type behaves the same way with a zero entitlement.
        assertThat(balance(f.employeeToken, null, "UNPAID").get("accruedToDate").decimalValue())
                .isEqualByComparingTo("0.00");
    }

    // ---------- join-date proration ----------

    @Test
    void joiningMidYear_proratesTheFirstYearsEntitlement() throws Exception {
        String slug = slug("prorate");
        String owner = register(slug);
        // Start the leave year 3 months ago, then hire someone today → 9 of 12 months left.
        setLeaveYearStartMonth(owner, monthsBeforeThisMonth(3));
        String email = "emp-" + slug + "@example.com";
        createEmployee(owner, email, "EMPLOYEE", LocalDate.now(), null);

        JsonNode annual = balance(login(email, "password"), null, "ANNUAL");
        // 16 x 9/12 = 12.00 — the join month counts as a whole month.
        assertThat(annual.get("entitled").decimalValue()).isEqualByComparingTo("12.00");
        assertThat(annual.get("accruedToDate").decimalValue()).isEqualByComparingTo("12.00");
    }

    @Test
    void anEmployeeWithNoJoinDate_isNotProrated() throws Exception {
        Fixture f = fixture("nojoin");
        setLeaveYearStartMonth(f.ownerToken, monthsBeforeThisMonth(6));
        assertThat(balance(f.employeeToken, null, "ANNUAL").get("entitled").decimalValue())
                .isEqualByComparingTo("16.00");
    }

    // ---------- carry-forward ----------

    @Test
    void unusedDaysCarryForward_butOnlyUpToTheTypeCap() throws Exception {
        Fixture f = fixture("carry");
        int year = currentLeaveYear(f.ownerToken);

        // ANNUAL caps carry-forward at 5 days; MEDICAL's cap (20) is above what's left.
        setCarryForwardCap(f.ownerToken, typeId(f.ownerToken, "ANNUAL"), "5");
        setCarryForwardCap(f.ownerToken, typeId(f.ownerToken, "MEDICAL"), "20");

        // Open LAST year first so it becomes the carry-forward source: 16 annual / 14
        // medical days, none used.
        adminBalances(f.ownerToken, f.employeeId, year - 1);

        JsonNode annual = balance(f.employeeToken, year, "ANNUAL");
        assertThat(annual.get("carriedForward").decimalValue()).isEqualByComparingTo("5.00");   // capped, not 16
        assertThat(annual.get("remaining").decimalValue()).isEqualByComparingTo("21.00");       // 16 + 5

        JsonNode medical = balance(f.employeeToken, year, "MEDICAL");
        assertThat(medical.get("carriedForward").decimalValue()).isEqualByComparingTo("14.00"); // under the cap
    }

    @Test
    void withNoCap_nothingCarriesForward() throws Exception {
        Fixture f = fixture("nocarry");
        int year = currentLeaveYear(f.ownerToken);
        adminBalances(f.ownerToken, f.employeeId, year - 1);

        assertThat(balance(f.employeeToken, year, "ANNUAL").get("carriedForward").decimalValue())
                .isEqualByComparingTo("0.00");
    }

    // ---------- admin entitlement override ----------

    @Test
    void ownerCanOverrideAnEntitlement() throws Exception {
        Fixture f = fixture("override");
        assertThat(balance(f.employeeToken, null, "ANNUAL").get("remaining").decimalValue())
                .isEqualByComparingTo("16.00");

        JsonNode updated = parse(mvc.perform(patch("/api/admin/leave/balances")
                        .header("Authorization", bearer(f.ownerToken))
                        .contentType("application/json")
                        .content(overrideBody(f.employeeId, typeId(f.ownerToken, "ANNUAL"), null, "22")))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString());

        assertThat(updated.get("entitled").decimalValue()).isEqualByComparingTo("22.00");
        assertThat(updated.get("remaining").decimalValue()).isEqualByComparingTo("22.00");
        assertThat(balance(f.employeeToken, null, "ANNUAL").get("remaining").decimalValue())
                .isEqualByComparingTo("22.00");
    }

    @Test
    void loweringAnEntitlementBelowWhatWasTaken_surfacesANegativeBalance() throws Exception {
        Fixture f = fixture("negative");
        String annualId = typeId(f.ownerToken, "ANNUAL");

        // Take (and approve) a day inside THIS leave year, then cut the entitlement to nothing.
        LocalDate inThisYear = nextWorkingDay(LeaveYear.startOf(currentLeaveYear(f.ownerToken), 1).plusDays(2));
        String requestId = applyFullDays(f.employeeToken, annualId, inThisYear, 1);
        mvc.perform(post("/api/admin/leave/requests/" + requestId + "/approve")
                        .header("Authorization", bearer(f.ownerToken)))
                .andExpect(status().isOk());

        mvc.perform(patch("/api/admin/leave/balances").header("Authorization", bearer(f.ownerToken))
                        .contentType("application/json")
                        .content(overrideBody(f.employeeId, annualId, null, "0")))
                .andExpect(status().isOk());

        assertThat(balance(f.employeeToken, null, "ANNUAL").get("remaining").decimalValue())
                .isEqualByComparingTo("-1.00");
    }

    @Test
    void overrideRejectsANegativeEntitlement() throws Exception {
        Fixture f = fixture("neg400");
        mvc.perform(patch("/api/admin/leave/balances").header("Authorization", bearer(f.ownerToken))
                        .contentType("application/json")
                        .content(overrideBody(f.employeeId, typeId(f.ownerToken, "ANNUAL"), null, "-1")))
                .andExpect(status().isBadRequest());
    }

    @Test
    void overrideIsForbiddenForManagersAndEmployees() throws Exception {
        Fixture f = fixture("rbac");
        String managerEmail = "mgr-" + f.slug + "@example.com";
        createEmployee(f.ownerToken, managerEmail, "MANAGER", null, null);
        String body = overrideBody(f.employeeId, typeId(f.ownerToken, "ANNUAL"), null, "20");

        mvc.perform(patch("/api/admin/leave/balances").header("Authorization", bearer(login(managerEmail, "password")))
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/leave/balances").header("Authorization", bearer(f.employeeToken))
                        .contentType("application/json").content(body))
                .andExpect(status().isForbidden());
    }

    @Test
    void balanceEndpointsRequireAuthentication() throws Exception {
        mvc.perform(get("/api/leave/balances")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/leave/balances").param("employeeId", java.util.UUID.randomUUID().toString()))
                .andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/admin/leave/balances").contentType("application/json")
                        .content(overrideBody(java.util.UUID.randomUUID().toString(),
                                java.util.UUID.randomUUID().toString(), null, "5")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void adminBalancesAreScopedToTheCallersCompany() throws Exception {
        Fixture a = fixture("tenantA");
        Fixture b = fixture("tenantB");
        mvc.perform(get("/api/admin/leave/balances").param("employeeId", b.employeeId)
                        .header("Authorization", bearer(a.ownerToken)))
                .andExpect(status().isNotFound());
    }

    @Test
    void anEmployeeCannotReadTheAdminBalanceList() throws Exception {
        Fixture f = fixture("empread");
        mvc.perform(get("/api/admin/leave/balances").param("employeeId", f.employeeId)
                        .header("Authorization", bearer(f.employeeToken)))
                .andExpect(status().isForbidden());
    }

    // ---------- company leave-year setting ----------

    @Test
    void leaveYearStartMonthMustBeAValidMonth() throws Exception {
        Fixture f = fixture("startmonth");
        for (String bad : new String[]{"0", "13", "-1"}) {
            mvc.perform(patch("/api/admin/company-settings").header("Authorization", bearer(f.ownerToken))
                            .contentType("application/json")
                            .content("{\"leaveYearStartMonth\":" + bad + "}"))
                    .andExpect(status().isBadRequest());
        }
        mvc.perform(patch("/api/admin/company-settings").header("Authorization", bearer(f.ownerToken))
                        .contentType("application/json").content("{\"leaveYearStartMonth\":4}"))
                .andExpect(status().isOk());
        assertThat(settings(f.ownerToken).get("leaveYearStartMonth").asInt()).isEqualTo(4);
    }

    // ---------- lazy provisioning ----------

    @Test
    void provisioningIsIdempotent() throws Exception {
        Fixture f = fixture("idem");
        JsonNode first = balances(f.employeeToken, null);
        JsonNode second = balances(f.employeeToken, null);

        assertThat(second.size()).isEqualTo(first.size());
        Set<String> ids = new HashSet<>();
        for (JsonNode b : second) {
            assertThat(ids.add(b.get("leaveTypeId").asText())).as("no duplicate rows per type").isTrue();
        }
        // The four seeded active types: ANNUAL, MEDICAL, EMERGENCY, UNPAID.
        assertThat(ids).hasSize(4);
    }

    // ---------- charging the right leave year ----------

    @Test
    void leaveDatedInTheNextLeaveYear_chargesThatYearsBalance() throws Exception {
        Fixture f = fixture("nextyear");
        int year = currentLeaveYear(f.ownerToken);
        LocalDate inNextYear = nextWorkingDay(LeaveYear.startOf(year + 1, 1));

        String requestId = applyFullDays(f.employeeToken, typeId(f.ownerToken, "ANNUAL"), inNextYear, 1);
        mvc.perform(post("/api/admin/leave/requests/" + requestId + "/approve")
                        .header("Authorization", bearer(f.ownerToken)))
                .andExpect(status().isOk());

        assertThat(balance(f.employeeToken, year, "ANNUAL").get("used").decimalValue())
                .as("this year's row is untouched")
                .isEqualByComparingTo("0.00");
        assertThat(balance(f.employeeToken, year + 1, "ANNUAL").get("used").decimalValue())
                .as("next year's row is charged")
                .isEqualByComparingTo("1.00");
    }

    // ================= helpers =================

    private record Fixture(String slug, String ownerToken, String employeeToken, String employeeId) {}

    /** A fresh company with an OWNER and one EMPLOYEE (no join date → never prorated). */
    private Fixture fixture(String prefix) throws Exception {
        String slug = slug(prefix);
        String owner = register(slug);
        String email = "emp-" + slug + "@example.com";
        String employeeId = createEmployee(owner, email, "EMPLOYEE", null, null);
        return new Fixture(slug, owner, login(email, "password"), employeeId);
    }

    private static String slug(String prefix) {
        return prefix + System.nanoTime();
    }

    private String register(String slug) throws Exception {
        String body = """
                {"companyName":"LA %s","fullName":"Owner %s","email":"laowner-%s@example.com","password":"password"}"""
                .formatted(slug, slug, slug);
        return parse(mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("accessToken").asText();
    }

    /** Create an employee; returns their id. */
    private String createEmployee(String adminToken, String email, String role,
                                  LocalDate joinDate, String fullName) throws Exception {
        StringBuilder body = new StringBuilder("{\"fullName\":\"")
                .append(fullName == null ? "Staff " + email : fullName)
                .append("\",\"email\":\"").append(email)
                .append("\",\"password\":\"password\",\"role\":\"").append(role).append("\"");
        if (joinDate != null) {
            body.append(",\"joinDate\":\"").append(joinDate).append("\"");
        }
        body.append("}");
        return parse(mvc.perform(post("/api/employees").header("Authorization", bearer(adminToken))
                        .contentType("application/json").content(body.toString()))
                .andExpect(status().isCreated()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
    }

    private void setLeaveYearStartMonth(String ownerToken, int month) throws Exception {
        mvc.perform(patch("/api/admin/company-settings").header("Authorization", bearer(ownerToken))
                        .contentType("application/json")
                        .content("{\"leaveYearStartMonth\":" + month + "}"))
                .andExpect(status().isOk());
    }

    private void setCarryForwardCap(String ownerToken, String leaveTypeId, String cap) throws Exception {
        mvc.perform(patch("/api/admin/leave/types/" + leaveTypeId).header("Authorization", bearer(ownerToken))
                        .contentType("application/json")
                        .content("{\"carryForwardMaxDays\":" + cap + "}"))
                .andExpect(status().isOk());
    }

    private void createType(String ownerToken, String name, String code, int days,
                            String accrual, String carryForwardMaxDays) throws Exception {
        String body = "{\"name\":\"%s\",\"code\":\"%s\",\"defaultEntitlementDays\":%d,\"accrual\":\"%s\"%s}"
                .formatted(name, code, days, accrual,
                        carryForwardMaxDays == null ? "" : ",\"carryForwardMaxDays\":" + carryForwardMaxDays);
        mvc.perform(post("/api/admin/leave/types").header("Authorization", bearer(ownerToken))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated());
    }

    private static String overrideBody(String employeeId, String leaveTypeId, Integer year, String entitled) {
        return "{\"employeeId\":\"%s\",\"leaveTypeId\":\"%s\"%s,\"entitled\":%s}"
                .formatted(employeeId, leaveTypeId, year == null ? "" : ",\"leaveYear\":" + year, entitled);
    }

    /** Apply for {@code days} consecutive working days starting at {@code start}; returns the request id. */
    private String applyFullDays(String token, String leaveTypeId, LocalDate start, int days) throws Exception {
        LocalDate end = start;
        for (int i = 1; i < days; i++) {
            end = nextWorkingDay(end.plusDays(1));
        }
        String body = "{\"leaveTypeId\":\"%s\",\"startDate\":\"%s\",\"endDate\":\"%s\",\"reason\":\"Test\"}"
                .formatted(leaveTypeId, start, end);
        return parse(mvc.perform(post("/api/leave/requests").header("Authorization", bearer(token))
                        .contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("id").asText();
    }

    /** The first Mon–Fri day on or after {@code from} (fresh companies work Mon–Fri with no holidays). */
    private static LocalDate nextWorkingDay(LocalDate from) {
        LocalDate d = from;
        while (d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY) {
            d = d.plusDays(1);
        }
        return d;
    }

    /** A start month that puts today's month at index {@code offset} within the leave year. */
    private static int monthsBeforeThisMonth(int offset) {
        return ((LocalDate.now().getMonthValue() - 1 - offset + 12) % 12) + 1;
    }

    private int currentLeaveYear(String ownerToken) throws Exception {
        return LeaveYear.yearOf(LocalDate.now(), settings(ownerToken).get("leaveYearStartMonth").asInt());
    }

    private JsonNode settings(String token) throws Exception {
        return parse(mvc.perform(get("/api/admin/company-settings").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private String typeId(String token, String code) throws Exception {
        for (JsonNode t : parse(mvc.perform(get("/api/admin/leave/types").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())) {
            if (code.equals(t.get("code").asText())) {
                return t.get("id").asText();
            }
        }
        throw new AssertionError("No leave type with code " + code);
    }

    private JsonNode balances(String token, Integer year) throws Exception {
        var req = get("/api/leave/balances").header("Authorization", bearer(token));
        if (year != null) {
            req = req.param("year", String.valueOf(year));
        }
        return parse(mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode adminBalances(String token, String employeeId, Integer year) throws Exception {
        var req = get("/api/admin/leave/balances").param("employeeId", employeeId)
                .header("Authorization", bearer(token));
        if (year != null) {
            req = req.param("year", String.valueOf(year));
        }
        return parse(mvc.perform(req).andExpect(status().isOk()).andReturn().getResponse().getContentAsString());
    }

    private JsonNode balance(String token, Integer year, String code) throws Exception {
        for (JsonNode b : balances(token, year)) {
            if (code.equals(b.get("code").asText())) {
                return b;
            }
        }
        throw new AssertionError("No balance for leave type " + code);
    }

    private JsonNode parse(String json) {
        try {
            return om.readTree(json);
        } catch (Exception e) {
            throw new AssertionError(e);
        }
    }
}
