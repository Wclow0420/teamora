package com.teamora.payroll;

import com.teamora.attendance.AttendanceRepository;
import com.teamora.calendar.CompanyEventRepository;
import com.teamora.calendar.EventType;
import com.teamora.common.WorkWeek;
import com.teamora.company.Company;
import com.teamora.company.CompanySettings;
import com.teamora.company.CompanySettingsService;
import com.teamora.employee.Employee;
import com.teamora.employee.PayBasis;
import com.teamora.leave.HalfDayPeriod;
import com.teamora.leave.LeaveDurationUnit;
import com.teamora.leave.LeaveRequest;
import com.teamora.leave.LeaveStatus;
import com.teamora.leave.LeaveType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.when;

/**
 * Unit tests for schedule resolution + rate derivation + unpaid-day deduction math,
 * pinned to the sen. Repositories are mocked so this exercises the pure calculation.
 */
@ExtendWith(MockitoExtension.class)
class CompensationServiceTest {

    @Mock CompanySettingsService companySettings;
    @Mock com.teamora.leave.LeaveRequestRepository leaveRequests;
    @Mock CompanyEventRepository companyEvents;
    @Mock AttendanceRepository attendance;

    @InjectMocks CompensationService service;

    private Company company;

    @BeforeEach
    void setUp() {
        company = Company.builder().id(UUID.randomUUID()).name("Acme").slug("acme")
                .timezone("Asia/Kuala_Lumpur").currency("MYR").active(true).build();
        // No leave / holidays / attendance unless a test overrides.
        lenient().when(leaveRequests.findApprovedOverlapping(any(), any(), any(), any())).thenReturn(List.of());
        lenient().when(companyEvents.findByCompanyIdAndEventTypeAndEventDateBetween(any(), eq(EventType.HOLIDAY), any(), any()))
                .thenReturn(List.of());
        lenient().when(attendance.findByEmployeeInRange(any(), any(), any())).thenReturn(List.of());
    }

    private CompanySettings settings(PayBasis basis, int mask, String hoursPerDay) {
        CompanySettings s = new CompanySettings();
        s.setCompany(company);
        s.setDefaultPayBasis(basis);
        s.setDefaultWorkingDays((short) mask);
        s.setDefaultHoursPerDay(new BigDecimal(hoursPerDay));
        return s;
    }

    private Employee employee(String monthlySalary) {
        Employee e = Employee.builder()
                .id(UUID.randomUUID())
                .email("x@acme.com").passwordHash("h").fullName("X").active(true)
                .monthlySalary(new BigDecimal(monthlySalary))
                .build();
        e.setCompany(company);
        return e;
    }

    @Test
    void derive_monToFri_february2026() {
        // Feb 2026 has exactly 20 Mon–Fri days. RM4,000 → RM200/day, RM25/hour (8h).
        when(companySettings.resolve(any())).thenReturn(settings(PayBasis.MONTHLY, WorkWeek.MON_TO_FRI, "8.00"));

        CompensationService.Derivation d = service.derive(employee("4000"), YearMonth.of(2026, 2));

        assertThat(d.scheduledWorkingDays()).isEqualTo(20);
        assertThat(d.dailyRate()).isEqualByComparingTo("200.00");
        assertThat(d.hourlyRate()).isEqualByComparingTo("25.00");
    }

    @Test
    void derive_fullWeek_thirtyOneDayMonth() {
        // Full week over a 31-day month → 31 scheduled days. RM3,100 → RM100/day, RM12.50/hour.
        when(companySettings.resolve(any())).thenReturn(settings(PayBasis.MONTHLY, WorkWeek.FULL_WEEK, "8.00"));

        CompensationService.Derivation d = service.derive(employee("3100"), YearMonth.of(2026, 1));

        assertThat(d.scheduledWorkingDays()).isEqualTo(31);
        assertThat(d.dailyRate()).isEqualByComparingTo("100.00");
        assertThat(d.hourlyRate()).isEqualByComparingTo("12.50");
    }

    @Test
    void monthly_noUnpaidLeave_paidBasicEqualsSalary() {
        when(companySettings.resolve(any())).thenReturn(settings(PayBasis.MONTHLY, WorkWeek.MON_TO_FRI, "8.00"));

        CompensationService.Compensation c = service.forPeriod(employee("4000"), YearMonth.of(2026, 2));

        assertThat(c.unpaidDays()).isEqualByComparingTo("0");
        assertThat(c.unpaidDeduction()).isEqualByComparingTo("0.00");
        assertThat(c.paidBasic()).isEqualByComparingTo("4000.00");
    }

    @Test
    void monthly_twoUnpaidDays_deductsAtDailyRate() {
        // Feb 2026, 20 scheduled days, RM4,000 → RM200/day. 2 unpaid weekdays → deduct RM400.
        when(companySettings.resolve(any())).thenReturn(settings(PayBasis.MONTHLY, WorkWeek.MON_TO_FRI, "8.00"));
        Employee e = employee("4000");
        // Mon 2 Feb + Tue 3 Feb 2026 (both weekdays).
        LeaveRequest unpaid = approvedUnpaid(e, LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 3));
        when(leaveRequests.findApprovedOverlapping(eq(e.getId()), eq(LeaveStatus.APPROVED), any(), any()))
                .thenReturn(List.of(unpaid));

        CompensationService.Compensation c = service.forPeriod(e, YearMonth.of(2026, 2));

        assertThat(c.unpaidDays()).isEqualByComparingTo("2");
        assertThat(c.unpaidDeduction()).isEqualByComparingTo("400.00");
        assertThat(c.paidBasic()).isEqualByComparingTo("3600.00");
        assertThat(c.paidDays()).isEqualTo(18);
    }

    @Test
    void monthly_unpaidDeduction_toTheSen() {
        // March 2026 has 22 Mon–Fri days. RM4,000 / 22 = RM181.818… → 1 unpaid day deducts RM181.82.
        when(companySettings.resolve(any())).thenReturn(settings(PayBasis.MONTHLY, WorkWeek.MON_TO_FRI, "8.00"));
        Employee e = employee("4000");
        LeaveRequest unpaid = approvedUnpaid(e, LocalDate.of(2026, 3, 2), LocalDate.of(2026, 3, 2)); // Mon 2 Mar
        when(leaveRequests.findApprovedOverlapping(eq(e.getId()), eq(LeaveStatus.APPROVED), any(), any()))
                .thenReturn(List.of(unpaid));

        CompensationService.Compensation c = service.forPeriod(e, YearMonth.of(2026, 3));

        assertThat(c.scheduledWorkingDays()).isEqualTo(22);
        assertThat(c.unpaidDays()).isEqualByComparingTo("1");
        assertThat(c.unpaidDeduction()).isEqualByComparingTo("181.82");
        assertThat(c.paidBasic()).isEqualByComparingTo("3818.18");
    }

    @Test
    void monthly_paidLeave_doesNotDeduct() {
        when(companySettings.resolve(any())).thenReturn(settings(PayBasis.MONTHLY, WorkWeek.MON_TO_FRI, "8.00"));
        Employee e = employee("4000");
        LeaveRequest paid = approvedPaid(e, LocalDate.of(2026, 2, 2), LocalDate.of(2026, 2, 4));
        when(leaveRequests.findApprovedOverlapping(eq(e.getId()), eq(LeaveStatus.APPROVED), any(), any()))
                .thenReturn(List.of(paid));

        CompensationService.Compensation c = service.forPeriod(e, YearMonth.of(2026, 2));

        assertThat(c.unpaidDays()).isEqualByComparingTo("0");
        assertThat(c.paidBasic()).isEqualByComparingTo("4000.00");
    }

    @Test
    void monthly_unpaidHalfDay_deductsExactlyHalfTheDailyRate() {
        // Feb 2026: 20 scheduled days, RM4,000 → RM200/day. Half a day → RM100.00.
        when(companySettings.resolve(any())).thenReturn(settings(PayBasis.MONTHLY, WorkWeek.MON_TO_FRI, "8.00"));
        Employee e = employee("4000");
        LeaveRequest half = halfDay(e, LocalDate.of(2026, 2, 2), false);
        when(leaveRequests.findApprovedOverlapping(eq(e.getId()), eq(LeaveStatus.APPROVED), any(), any()))
                .thenReturn(List.of(half));

        CompensationService.Compensation c = service.forPeriod(e, YearMonth.of(2026, 2));

        assertThat(c.unpaidDays()).isEqualByComparingTo("0.50");
        assertThat(c.unpaidDeduction()).isEqualByComparingTo("100.00");
        assertThat(c.paidBasic()).isEqualByComparingTo("3900.00");
    }

    @Test
    void monthly_unpaidTwoHours_deductsAQuarterOfTheDailyRate() {
        // 2h of an 8h day = 0.25 day → RM200 × 0.25 = RM50.00.
        when(companySettings.resolve(any())).thenReturn(settings(PayBasis.MONTHLY, WorkWeek.MON_TO_FRI, "8.00"));
        Employee e = employee("4000");
        LeaveRequest twoHours = hours(e, LocalDate.of(2026, 2, 2), "2.00", false);
        when(leaveRequests.findApprovedOverlapping(eq(e.getId()), eq(LeaveStatus.APPROVED), any(), any()))
                .thenReturn(List.of(twoHours));

        CompensationService.Compensation c = service.forPeriod(e, YearMonth.of(2026, 2));

        assertThat(c.unpaidDays()).isEqualByComparingTo("0.25");
        assertThat(c.unpaidDeduction()).isEqualByComparingTo("50.00");
        assertThat(c.paidBasic()).isEqualByComparingTo("3950.00");
    }

    @Test
    void monthly_unpaidLeaveOverAWeekend_onlyCountsWorkingDays() {
        // Fri 6 → Mon 9 Feb 2026 is 4 calendar days but only 2 Mon–Fri working days.
        when(companySettings.resolve(any())).thenReturn(settings(PayBasis.MONTHLY, WorkWeek.MON_TO_FRI, "8.00"));
        Employee e = employee("4000");
        LeaveRequest unpaid = approvedUnpaid(e, LocalDate.of(2026, 2, 6), LocalDate.of(2026, 2, 9));
        when(leaveRequests.findApprovedOverlapping(eq(e.getId()), eq(LeaveStatus.APPROVED), any(), any()))
                .thenReturn(List.of(unpaid));

        CompensationService.Compensation c = service.forPeriod(e, YearMonth.of(2026, 2));

        assertThat(c.unpaidDays()).isEqualByComparingTo("2.00");
        assertThat(c.unpaidDeduction()).isEqualByComparingTo("400.00");
    }

    @Test
    void monthly_paidHalfDay_doesNotDeduct() {
        when(companySettings.resolve(any())).thenReturn(settings(PayBasis.MONTHLY, WorkWeek.MON_TO_FRI, "8.00"));
        Employee e = employee("4000");
        when(leaveRequests.findApprovedOverlapping(eq(e.getId()), eq(LeaveStatus.APPROVED), any(), any()))
                .thenReturn(List.of(halfDay(e, LocalDate.of(2026, 2, 2), true)));

        CompensationService.Compensation c = service.forPeriod(e, YearMonth.of(2026, 2));

        assertThat(c.unpaidDays()).isEqualByComparingTo("0");
        assertThat(c.paidBasic()).isEqualByComparingTo("4000.00");
    }

    // ---- helpers ----

    private LeaveRequest approvedUnpaid(Employee e, LocalDate from, LocalDate to) {
        return approved(e, from, to, leaveType("UNPAID", false));
    }

    private LeaveRequest approvedPaid(Employee e, LocalDate from, LocalDate to) {
        return approved(e, from, to, leaveType("ANNUAL", true));
    }

    private LeaveRequest approved(Employee e, LocalDate from, LocalDate to, LeaveType type) {
        LeaveRequest r = LeaveRequest.builder()
                .id(UUID.randomUUID())
                .employee(e)
                .leaveType(type)
                .startDate(from)
                .endDate(to)
                .days(BigDecimal.valueOf(to.toEpochDay() - from.toEpochDay() + 1).setScale(2))
                .status(LeaveStatus.APPROVED)
                .build();
        r.setCompany(company);
        return r;
    }

    private LeaveRequest halfDay(Employee e, LocalDate on, boolean paid) {
        LeaveRequest r = approved(e, on, on, leaveType(paid ? "ANNUAL" : "UNPAID", paid));
        r.setDurationUnit(LeaveDurationUnit.HALF_DAY);
        r.setHalfDayPeriod(HalfDayPeriod.AM);
        r.setDays(new BigDecimal("0.50"));
        return r;
    }

    private LeaveRequest hours(Employee e, LocalDate on, String hrs, boolean paid) {
        LeaveRequest r = approved(e, on, on, leaveType(paid ? "ANNUAL" : "UNPAID", paid));
        r.setDurationUnit(LeaveDurationUnit.HOURS);
        r.setHours(new BigDecimal(hrs));
        r.setDays(new BigDecimal(hrs).divide(new BigDecimal("8.00"), 2, java.math.RoundingMode.HALF_UP));
        return r;
    }

    private LeaveType leaveType(String code, boolean paid) {
        LeaveType t = LeaveType.builder()
                .id(UUID.randomUUID()).name(code).code(code).paid(paid)
                .colorKey("coral").active(true).sortOrder(1)
                .accrual(com.teamora.leave.LeaveAccrual.FIXED_ANNUAL)
                .build();
        t.setCompany(company);
        return t;
    }
}
