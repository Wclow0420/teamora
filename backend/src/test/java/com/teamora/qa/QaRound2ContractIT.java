package com.teamora.qa;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamora.AbstractIntegrationTest;
import com.teamora.leave.LeaveBalance;
import com.teamora.leave.LeaveBalanceRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * QA round 2 — the JSON contract shared with the app:
 * real payslip bank line, drafts hidden from staff, unpaid fields on the run line,
 * no fake clock-in location, configurable work start / late grace, leave overlap +
 * balance guards, tightened validation, staff id / phone edits, and join-date
 * re-proration.
 *
 * <p>Every test registers its own throwaway company, so nothing leaks into the other
 * test classes sharing the test database.
 */
class QaRound2ContractIT extends AbstractIntegrationTest {

    @Autowired
    private LeaveBalanceRepository leaveBalanceRepository;

    // =====================================================================
    // 1 + 2 + 3 — payslips
    // =====================================================================

    @Test
    void payslip_isHiddenWhileDraft_thenShowsTheRealBankLine() throws Exception {
        String slug = slug("pay");
        String owner = register(slug);
        String fullEmail = "qa-bank-" + slug + "@example.com";
        String nameOnlyEmail = "qa-bankname-" + slug + "@example.com";
        String noneEmail = "qa-nobank-" + slug + "@example.com";
        createEmp(owner, "Bank Full", fullEmail,
                ",\"monthlySalary\":4000,\"bankName\":\"Maybank\",\"bankAccountNo\":\"5140 1234 5678\"");
        createEmp(owner, "Bank Name", nameOnlyEmail, ",\"monthlySalary\":4000,\"bankName\":\"CIMB\"");
        createEmp(owner, "Bank None", noneEmail, ",\"monthlySalary\":4000");
        String full = login(fullEmail, "password");

        JsonNode run = json(mvc.perform(post("/api/admin/payroll/run").header("Authorization", bearer(owner))
                        .contentType("application/json").content("{\"period\":\"2026-07\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DRAFT")));
        // Contract 3: every line carries the unpaid fields; no unpaid leave → null deduction label.
        JsonNode line = run.get("lines").get(0);
        assertThat(line.get("unpaidDays").decimalValue()).isEqualByComparingTo("0");
        assertThat(line.get("unpaidDaysLabel").asText()).isEqualTo("None");
        assertThat(line.has("unpaidDeductionLabel")).isTrue();
        assertThat(line.get("unpaidDeductionLabel").isNull()).isTrue();

        // Contract 2: a DRAFT is not final — hidden from the list and 404 by period.
        mvc.perform(get("/api/payroll/payslips").header("Authorization", bearer(full)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
        mvc.perform(get("/api/payroll/payslips/2026-07").header("Authorization", bearer(full)))
                .andExpect(status().isNotFound());

        mvc.perform(post("/api/admin/payroll/run/2026-07/approve").header("Authorization", bearer(owner)))
                .andExpect(status().isOk());

        // APPROVED → visible, with the real bank line (contract 1).
        JsonNode slip = json(mvc.perform(get("/api/payroll/payslips/2026-07").header("Authorization", bearer(full)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.statusLabel").value("Approved")));
        assertThat(slip.get("bankLabel").asText()).isEqualTo("Maybank ••5678");
        mvc.perform(get("/api/payroll/payslips").header("Authorization", bearer(full)))
                .andExpect(jsonPath("$.length()").value(1));

        // Bank name only → just the name; nothing on record → null (never a placeholder).
        JsonNode nameOnly = json(mvc.perform(get("/api/payroll/payslips/2026-07")
                .header("Authorization", bearer(login(nameOnlyEmail, "password")))).andExpect(status().isOk()));
        assertThat(nameOnly.get("bankLabel").asText()).isEqualTo("CIMB");
        mvc.perform(get("/api/payroll/payslips/2026-07").header("Authorization", bearer(login(noneEmail, "password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.bankLabel").value(nullValue()));

        // PAID stays visible.
        mvc.perform(post("/api/admin/payroll/run/2026-07/mark-paid").header("Authorization", bearer(owner)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/payroll/payslips/2026-07").header("Authorization", bearer(full)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PAID"));
    }

    @Test
    void payslips_requireAuthentication() throws Exception {
        mvc.perform(get("/api/payroll/payslips")).andExpect(status().isUnauthorized());
    }

    // =====================================================================
    // 4 — no fake location
    // =====================================================================

    @Test
    void clockIn_withoutAnAssignedSite_recordsNoLocation() throws Exception {
        String slug = slug("loc");
        String owner = register(slug);
        String email = "qa-loc-" + slug + "@example.com";
        String empId = createEmp(owner, "No Site", email, "");
        String emp = login(email, "password");

        // The profile reports no work location at all (not the company name / address).
        mvc.perform(get("/api/employees/me").header("Authorization", bearer(emp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location").value(nullValue()))
                .andExpect(jsonPath("$.workLocationName").value(nullValue()));

        mvc.perform(post("/api/attendance/clock-in").header("Authorization", bearer(emp))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location").value(nullValue()));
        mvc.perform(get("/api/attendance/today").header("Authorization", bearer(emp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.location").value(nullValue()));

        // Admin live board: the row exposes `location`, null for someone with no site.
        JsonNode live = json(mvc.perform(get("/api/admin/attendance/live").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()));
        JsonNode row = null;
        for (JsonNode r : live.get("staff")) {
            if (empId.equals(r.get("employeeId").asText())) {
                row = r;
            }
        }
        assertThat(row).isNotNull();
        assertThat(row.has("location")).isTrue();
        assertThat(row.get("location").isNull()).isTrue();

        // Once a real site is assigned, the profile reports that site's name.
        String siteId = json(mvc.perform(post("/api/admin/work-locations").header("Authorization", bearer(owner))
                        .contentType("application/json")
                        .content("{\"name\":\"Real Shop\",\"latitude\":3.150000,\"longitude\":101.700000}"))
                .andExpect(status().isCreated())).get("id").asText();
        mvc.perform(patch("/api/employees/" + empId).header("Authorization", bearer(owner))
                        .contentType("application/json").content("{\"workLocationId\":\"" + siteId + "\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/employees/me").header("Authorization", bearer(emp)))
                .andExpect(jsonPath("$.location").value("Real Shop"))
                .andExpect(jsonPath("$.workLocationName").value("Real Shop"));
    }

    // =====================================================================
    // 5 — work start time + late grace
    // =====================================================================

    @Test
    void companySettings_exposeAndUpdateWorkStartAndLateGrace() throws Exception {
        String slug = slug("ws");
        String owner = register(slug);
        String email = "qa-ws-" + slug + "@example.com";
        createEmp(owner, "Settings Emp", email, "");

        mvc.perform(get("/api/admin/company-settings").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workStartTime").value("09:00"))
                .andExpect(jsonPath("$.lateGraceMinutes").value(5));

        patchSettings(owner, "{\"workStartTime\":\"08:30\",\"lateGraceMinutes\":15}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workStartTime").value("08:30"))
                .andExpect(jsonPath("$.lateGraceMinutes").value(15))
                .andExpect(jsonPath("$.defaultWorkingDays").value(31));   // untouched
        mvc.perform(get("/api/admin/company-settings").header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.workStartTime").value("08:30"))
                .andExpect(jsonPath("$.lateGraceMinutes").value(15));

        // Validation → 400.
        patchSettings(owner, "{\"workStartTime\":\"8:30\"}").andExpect(status().isBadRequest());
        patchSettings(owner, "{\"workStartTime\":\"25:00\"}").andExpect(status().isBadRequest());
        patchSettings(owner, "{\"lateGraceMinutes\":121}").andExpect(status().isBadRequest());
        patchSettings(owner, "{\"lateGraceMinutes\":-1}").andExpect(status().isBadRequest());
        patchSettings(owner, "{\"defaultWorkingDays\":0}").andExpect(status().isBadRequest());

        // Permissions: staff 403, anonymous 401.
        patchSettings(login(email, "password"), "{\"lateGraceMinutes\":10}").andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/company-settings").contentType("application/json")
                        .content("{\"lateGraceMinutes\":10}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void clockIn_isLateOnlyAfterWorkStartPlusGrace() throws Exception {
        // Start 00:00 with no grace → any clock-in today is late.
        String lateSlug = slug("late");
        String lateOwner = register(lateSlug);
        patchSettings(lateOwner, "{\"workStartTime\":\"00:00\",\"lateGraceMinutes\":0}").andExpect(status().isOk());
        String lateEmail = "qa-late-" + lateSlug + "@example.com";
        createEmp(lateOwner, "Late Emp", lateEmail, "");
        mvc.perform(post("/api/attendance/clock-in").header("Authorization", bearer(login(lateEmail, "password")))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("LATE"));

        // Start 23:59 + 120 min grace → nobody can be late today.
        String okSlug = slug("ontime");
        String okOwner = register(okSlug);
        patchSettings(okOwner, "{\"workStartTime\":\"23:59\",\"lateGraceMinutes\":120}").andExpect(status().isOk());
        String okEmail = "qa-ontime-" + okSlug + "@example.com";
        createEmp(okOwner, "On Time Emp", okEmail, "");
        mvc.perform(post("/api/attendance/clock-in").header("Authorization", bearer(login(okEmail, "password")))
                        .contentType("application/json").content("{}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PRESENT"));
    }

    // =====================================================================
    // 6 — leave overlap + balance
    // =====================================================================

    @Test
    void leaveApply_rejectsOverlappingRequests() throws Exception {
        String slug = slug("ovl");
        String owner = register(slug);
        String email = "qa-ovl-" + slug + "@example.com";
        createEmp(owner, "Overlap Emp", email, "");
        String emp = login(email, "password");
        String annual = leaveTypeId(emp, "ANNUAL");

        // Mon 2 – Wed 4 Nov 2026, full days.
        applyLeave(emp, annual, "2026-11-02", "2026-11-04", "").andExpect(status().isOk());

        // Any request touching those dates is an overlap — full, half or hourly.
        assertMessage(applyLeave(emp, annual, "2026-11-04", "2026-11-05", "").andExpect(status().isBadRequest()),
                "You already have a leave request covering these dates.");
        applyLeave(emp, annual, "2026-11-03", "2026-11-03", ",\"durationUnit\":\"HALF_DAY\",\"halfDayPeriod\":\"AM\"")
                .andExpect(status().isBadRequest());
        applyLeave(emp, annual, "2026-11-02", "2026-11-02", ",\"durationUnit\":\"HOURS\",\"hours\":2")
                .andExpect(status().isBadRequest());

        // A morning and an afternoon half day on the same date coexist…
        applyLeave(emp, annual, "2026-11-09", "2026-11-09", ",\"durationUnit\":\"HALF_DAY\",\"halfDayPeriod\":\"AM\"")
                .andExpect(status().isOk());
        applyLeave(emp, annual, "2026-11-09", "2026-11-09", ",\"durationUnit\":\"HALF_DAY\",\"halfDayPeriod\":\"PM\"")
                .andExpect(status().isOk());
        // …but the same half twice, or a full day on top, does not.
        applyLeave(emp, annual, "2026-11-09", "2026-11-09", ",\"durationUnit\":\"HALF_DAY\",\"halfDayPeriod\":\"AM\"")
                .andExpect(status().isBadRequest());
        applyLeave(emp, annual, "2026-11-09", "2026-11-09", "").andExpect(status().isBadRequest());

        // Hourly requests on the same date coexist; a full day over them does not.
        applyLeave(emp, annual, "2026-11-10", "2026-11-10", ",\"durationUnit\":\"HOURS\",\"hours\":2")
                .andExpect(status().isOk());
        applyLeave(emp, annual, "2026-11-10", "2026-11-10", ",\"durationUnit\":\"HOURS\",\"hours\":1")
                .andExpect(status().isOk());
        applyLeave(emp, annual, "2026-11-10", "2026-11-11", "").andExpect(status().isBadRequest());

        // A REJECTED request no longer blocks its dates.
        String id = json(applyLeave(emp, annual, "2026-11-16", "2026-11-16", "").andExpect(status().isOk()))
                .get("id").asText();
        mvc.perform(post("/api/admin/leave/requests/" + id + "/reject").header("Authorization", bearer(owner)))
                .andExpect(status().isOk());
        applyLeave(emp, annual, "2026-11-16", "2026-11-16", "").andExpect(status().isOk());
    }

    @Test
    void leaveApply_rejectsMoreThanTheAvailableBalance_butNeverBlocksUntrackedTypes() throws Exception {
        String slug = slug("bal");
        String owner = register(slug);
        String email = "qa-bal-" + slug + "@example.com";
        createEmp(owner, "Balance Emp", email, "");
        String emp = login(email, "password");
        String annual = leaveTypeId(emp, "ANNUAL");   // 16 days, FIXED_ANNUAL
        String unpaid = leaveTypeId(emp, "UNPAID");   // accrual NONE

        // Mon 2 – Tue 24 Nov 2026 = 17 working days > 16.
        ResultActions tooMany = applyLeave(emp, annual, "2026-11-02", "2026-11-24", "")
                .andExpect(status().isBadRequest());
        assertThat(message(tooMany)).startsWith("Not enough Annual Leave balance")
                .endsWith("you have 16 day(s) available.");

        // 10 days pending (2–13 Nov) → only 6 left to ask for.
        applyLeave(emp, annual, "2026-11-02", "2026-11-13", "").andExpect(status().isOk());
        ResultActions overPending = applyLeave(emp, annual, "2026-11-16", "2026-11-24", "")   // 7 days
                .andExpect(status().isBadRequest());
        assertThat(message(overPending)).endsWith("you have 6 day(s) available.");
        applyLeave(emp, annual, "2026-11-16", "2026-11-23", "").andExpect(status().isOk());   // exactly 6

        // Unpaid leave has no entitlement to run out of.
        applyLeave(emp, unpaid, "2026-12-01", "2026-12-31", "").andExpect(status().isOk());
    }

    // =====================================================================
    // 7 — validation
    // =====================================================================

    @Test
    void validation_workingDaysMaskOfZero_isRejected() throws Exception {
        String slug = slug("wd");
        String owner = register(slug);
        mvc.perform(post("/api/employees").header("Authorization", bearer(owner))
                        .contentType("application/json")
                        .content("{\"fullName\":\"No Days\",\"email\":\"qa-wd0-%s@example.com\",\"password\":\"password\",\"role\":\"EMPLOYEE\",\"workingDays\":0}"
                                .formatted(slug)))
                .andExpect(status().isBadRequest());
        String empId = createEmp(owner, "Some Days", "qa-wd-" + slug + "@example.com", ",\"workingDays\":31");
        mvc.perform(patch("/api/employees/" + empId).header("Authorization", bearer(owner))
                        .contentType("application/json").content("{\"workingDays\":0}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/employees/" + empId).header("Authorization", bearer(owner))
                        .contentType("application/json").content("{\"workingDays\":63}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workingDays").value(63));
    }

    @Test
    void validation_blankCompanyName_isRejected_andLeavesTheNameAlone() throws Exception {
        String slug = slug("cn");
        String owner = register(slug);
        mvc.perform(patch("/api/companies/me").header("Authorization", bearer(owner))
                        .contentType("application/json").content("{\"name\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/companies/me").header("Authorization", bearer(owner))
                        .contentType("application/json").content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
        // Omitting the name is still a valid partial update.
        mvc.perform(patch("/api/companies/me").header("Authorization", bearer(owner))
                        .contentType("application/json").content("{\"phone\":\"03-1234 5678\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("QA " + slug))
                .andExpect(jsonPath("$.phone").value("03-1234 5678"));
        // ...and it is actually stored: a fresh read returns it (the PATCH response
        // alone once echoed an edit that was never written).
        mvc.perform(get("/api/companies/me").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("03-1234 5678"));
    }

    @Test
    void validation_passwordsNeedEightCharacters() throws Exception {
        String slug = slug("pw");
        // Register.
        mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content("{\"companyName\":\"Short %s\",\"fullName\":\"O\",\"email\":\"qa-short-%s@example.com\",\"password\":\"1234567\"}"
                                .formatted(slug, slug)))
                .andExpect(status().isBadRequest());
        String owner = register(slug);   // "password" — exactly 8 — is fine

        // Create employee.
        mvc.perform(post("/api/employees").header("Authorization", bearer(owner))
                        .contentType("application/json")
                        .content("{\"fullName\":\"Short Pw\",\"email\":\"qa-shortemp-%s@example.com\",\"password\":\"1234567\",\"role\":\"EMPLOYEE\"}"
                                .formatted(slug)))
                .andExpect(status().isBadRequest());
        String empId = createEmp(owner, "Ok Pw", "qa-okpw-" + slug + "@example.com", "");

        // Change own password / admin reset.
        mvc.perform(post("/api/auth/change-password").header("Authorization", bearer(owner))
                        .contentType("application/json")
                        .content("{\"currentPassword\":\"password\",\"newPassword\":\"1234567\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/employees/" + empId + "/reset-password").header("Authorization", bearer(owner))
                        .contentType("application/json").content("{\"newPassword\":\"1234567\"}"))
                .andExpect(status().isBadRequest());
    }

    // =====================================================================
    // 8 — staff id + phone
    // =====================================================================

    @Test
    void admin_editsStaffIdAndPhone_staffIdUniqueWithinTheCompany() throws Exception {
        String slug = slug("sid");
        String owner = register(slug);
        String first = json(mvc.perform(post("/api/employees").header("Authorization", bearer(owner))
                        .contentType("application/json")
                        .content("{\"fullName\":\"First\",\"email\":\"qa-sid1-%s@example.com\",\"password\":\"password\",\"role\":\"EMPLOYEE\",\"staffId\":\" S-1 \",\"phone\":\" 012-345 6789 \"}"
                                .formatted(slug)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.staffId").value("S-1"))
                .andExpect(jsonPath("$.phone").value("012-345 6789"))).get("id").asText();

        // Duplicate on create → 400.
        mvc.perform(post("/api/employees").header("Authorization", bearer(owner))
                        .contentType("application/json")
                        .content("{\"fullName\":\"Dup\",\"email\":\"qa-sid-dup-%s@example.com\",\"password\":\"password\",\"role\":\"EMPLOYEE\",\"staffId\":\"S-1\"}"
                                .formatted(slug)))
                .andExpect(status().isBadRequest());

        String second = createEmp(owner, "Second", "qa-sid2-" + slug + "@example.com", "");
        // Duplicate on update (case-insensitively) → 400.
        mvc.perform(patch("/api/employees/" + second).header("Authorization", bearer(owner))
                        .contentType("application/json").content("{\"staffId\":\"s-1\"}"))
                .andExpect(status().isBadRequest());
        // A free id, a phone, and re-saving your own id are all fine.
        mvc.perform(patch("/api/employees/" + second).header("Authorization", bearer(owner))
                        .contentType("application/json").content("{\"staffId\":\"S-2\",\"phone\":\"019-000 1111\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.staffId").value("S-2"))
                .andExpect(jsonPath("$.phone").value("019-000 1111"));
        mvc.perform(patch("/api/employees/" + first).header("Authorization", bearer(owner))
                        .contentType("application/json").content("{\"staffId\":\"S-1\"}"))
                .andExpect(status().isOk());
        // Blank clears the phone (and the staff id).
        mvc.perform(patch("/api/employees/" + second).header("Authorization", bearer(owner))
                        .contentType("application/json").content("{\"phone\":\"\",\"staffId\":\"\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value(nullValue()))
                .andExpect(jsonPath("$.staffId").value(nullValue()));

        // Another company may reuse the same staff id.
        String otherSlug = slug("sid2");
        String otherOwner = register(otherSlug);
        mvc.perform(post("/api/employees").header("Authorization", bearer(otherOwner))
                        .contentType("application/json")
                        .content("{\"fullName\":\"Other\",\"email\":\"qa-sid-other-%s@example.com\",\"password\":\"password\",\"role\":\"EMPLOYEE\",\"staffId\":\"S-1\"}"
                                .formatted(otherSlug)))
                .andExpect(status().isCreated());

        // Staff can't edit someone else's profile.
        String empToken = login("qa-sid1-" + slug + "@example.com", "password");
        mvc.perform(patch("/api/employees/" + second).header("Authorization", bearer(empToken))
                        .contentType("application/json").content("{\"staffId\":\"S-9\"}"))
                .andExpect(status().isForbidden());
    }

    // =====================================================================
    // 9 — join date re-proration
    // =====================================================================

    @Test
    void joinDateChange_reproratesThisYearsBalances_butNeverAnOverride() throws Exception {
        String slug = slug("jd");
        String owner = register(slug);
        String annual = leaveTypeId(owner, "ANNUAL");
        String email = "qa-jd-" + slug + "@example.com";
        String empId = createEmp(owner, "Join Date", email, "");

        int y = LocalDate.now().getYear();   // demo companies run a January leave year

        // No join date → full entitlement (16 annual, 14 medical).
        assertThat(entitled(owner, empId, "ANNUAL")).isEqualByComparingTo("16");
        assertThat(entitled(owner, empId, "MEDICAL")).isEqualByComparingTo("14");

        // Joined 1 Jul this year → 6 of 12 months remain → half.
        setJoinDate(owner, empId, y + "-07-01");
        assertThat(entitled(owner, empId, "ANNUAL")).isEqualByComparingTo("8");
        assertThat(entitled(owner, empId, "MEDICAL")).isEqualByComparingTo("7");

        // Correcting it again moves the proration again (1 Oct → 3/12).
        setJoinDate(owner, empId, y + "-10-01");
        assertThat(entitled(owner, empId, "ANNUAL")).isEqualByComparingTo("4");

        // An admin override is sticky: a later join-date change leaves it alone.
        mvc.perform(patch("/api/admin/leave/balances").header("Authorization", bearer(owner))
                        .contentType("application/json")
                        .content("{\"employeeId\":\"%s\",\"leaveTypeId\":\"%s\",\"entitled\":20}".formatted(empId, annual)))
                .andExpect(status().isOk());
        setJoinDate(owner, empId, y + "-04-01");
        assertThat(entitled(owner, empId, "ANNUAL")).isEqualByComparingTo("20");      // override kept
        assertThat(entitled(owner, empId, "MEDICAL")).isEqualByComparingTo("10.5");   // 14 × 9/12
    }

    @Test
    void joinDateChange_neverDropsBelowDaysAlreadyUsed_andKeepsHandSetRows() throws Exception {
        String slug = slug("jdc");
        String owner = register(slug);
        String email = "qa-jdc-" + slug + "@example.com";
        String empId = createEmp(owner, "Join Clamp", email, "");
        String emp = login(email, "password");
        String annual = leaveTypeId(emp, "ANNUAL");

        // Take 10 annual days (Mon–Fri × 2 in November this year), approved → used = 10.
        int y = LocalDate.now().getYear();
        LocalDate monday = LocalDate.of(y, 11, 1).with(TemporalAdjusters.firstInMonth(DayOfWeek.MONDAY));
        String id = json(applyLeave(emp, annual, monday.toString(), monday.plusDays(11).toString(), "")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.durationLabel").value("10 days")))
                .get("id").asText();
        mvc.perform(post("/api/admin/leave/requests/" + id + "/approve").header("Authorization", bearer(owner)))
                .andExpect(status().isOk());

        // A balance row whose entitlement was set by hand BEFORE the override flag existed:
        // it no longer matches the formula, so it must be treated as an override.
        assertThat(entitled(owner, empId, "MEDICAL")).isEqualByComparingTo("14");   // provisions the row
        LeaveBalance medical = leaveBalanceRepository
                .findByEmployeeIdAndLeaveYear(UUID.fromString(empId), y).stream()
                .filter(b -> "MEDICAL".equals(b.getLeaveType().getCode())).findFirst().orElseThrow();
        medical.setEntitled(new BigDecimal("18.00"));
        leaveBalanceRepository.save(medical);

        // Joined 1 Oct this year → annual would prorate to 4, but 10 are already used → clamp to 10.
        setJoinDate(owner, empId, y + "-10-01");
        assertThat(entitled(owner, empId, "ANNUAL")).isEqualByComparingTo("10");
        assertThat(entitled(owner, empId, "MEDICAL")).isEqualByComparingTo("18");
    }

    // =====================================================================
    // helpers
    // =====================================================================

    private static String slug(String tag) {
        return tag + System.nanoTime();
    }

    /** Registers company "QA {slug}" and returns the owner's access token. */
    private String register(String slug) throws Exception {
        String body = """
                {"companyName":"QA %s","fullName":"Owner %s","email":"qa-owner-%s@example.com","password":"password"}"""
                .formatted(slug, slug, slug);
        return json(mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isOk())).get("accessToken").asText();
    }

    /** Creates an EMPLOYEE (password "password"); {@code extra} is appended raw JSON starting with a comma. */
    private String createEmp(String owner, String name, String email, String extra) throws Exception {
        String body = "{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"password\",\"role\":\"EMPLOYEE\"%s}"
                .formatted(name, email, extra);
        return json(mvc.perform(post("/api/employees").header("Authorization", bearer(owner))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())).get("id").asText();
    }

    private ResultActions patchSettings(String token, String body) throws Exception {
        return mvc.perform(patch("/api/admin/company-settings").header("Authorization", bearer(token))
                .contentType("application/json").content(body));
    }

    private ResultActions applyLeave(String token, String typeId, String start, String end, String extra) throws Exception {
        String body = "{\"leaveTypeId\":\"%s\",\"startDate\":\"%s\",\"endDate\":\"%s\",\"reason\":\"qa\"%s}"
                .formatted(typeId, start, end, extra);
        return mvc.perform(post("/api/leave/requests").header("Authorization", bearer(token))
                .contentType("application/json").content(body));
    }

    private void setJoinDate(String owner, String empId, String date) throws Exception {
        mvc.perform(patch("/api/employees/" + empId).header("Authorization", bearer(owner))
                        .contentType("application/json").content("{\"joinDate\":\"" + date + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.joinDate").value(date));
    }

    private BigDecimal entitled(String owner, String empId, String code) throws Exception {
        JsonNode rows = json(mvc.perform(get("/api/admin/leave/balances").param("employeeId", empId)
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk()));
        for (JsonNode row : rows) {
            if (code.equals(row.get("code").asText())) {
                return row.get("entitled").decimalValue();
            }
        }
        throw new AssertionError("No balance for " + code);
    }

    private JsonNode json(ResultActions actions) throws Exception {
        return om.readTree(actions.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8));
    }

    private String message(ResultActions actions) throws Exception {
        return json(actions).get("message").asText();
    }

    private void assertMessage(ResultActions actions, String expected) throws Exception {
        assertThat(message(actions)).isEqualTo(expected);
    }
}
