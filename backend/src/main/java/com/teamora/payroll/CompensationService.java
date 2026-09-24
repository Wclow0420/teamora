package com.teamora.payroll;

import com.teamora.attendance.AttendanceRecord;
import com.teamora.attendance.AttendanceRepository;
import com.teamora.attendance.AttendanceStatus;
import com.teamora.calendar.CompanyEvent;
import com.teamora.calendar.CompanyEventRepository;
import com.teamora.calendar.EventType;
import com.teamora.common.WorkWeek;
import com.teamora.company.CompanySettings;
import com.teamora.company.CompanySettingsService;
import com.teamora.employee.Employee;
import com.teamora.employee.PayBasis;
import com.teamora.leave.LeaveDurationCalculator;
import com.teamora.leave.LeaveDurationUnit;
import com.teamora.leave.LeaveRequest;
import com.teamora.leave.LeaveRequestRepository;
import com.teamora.leave.LeaveStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Resolves each employee's effective work schedule (their overrides, else the
 * company default), derives daily/hourly rates from the stored monthly salary for
 * a given month, and computes the <b>paid basic</b> for a payroll period — the
 * basic pay after unpaid-leave days are deducted, per the employee's
 * {@link PayBasis}. This paid basic is what feeds {@link PayrollCalculator}, so
 * unpaid leave correctly lowers statutory contributions too.
 *
 * <p>Rules (see the build spec): pay is schedule-driven with exceptions —
 * MONTHLY staff are paid for every scheduled working day except unpaid-leave days;
 * paid leave and public holidays never reduce pay; absence with no leave record
 * does not auto-deduct. Overtime is handled separately (EA statutory) and is not
 * touched here.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompensationService {

    private static final int RATE_SCALE = 8; // internal precision before the final money round
    private static final int MONEY_SCALE = 2;
    private static final int DAY_SCALE = 2;  // leave day fractions (0.50, 0.25, …)
    private static final BigDecimal FULL_DAY = BigDecimal.ONE.setScale(DAY_SCALE);
    private static final BigDecimal HALF_DAY = new BigDecimal("0.50");

    private static final Set<AttendanceStatus> PRESENT = Set.of(
            AttendanceStatus.PRESENT, AttendanceStatus.LATE,
            AttendanceStatus.REMOTE, AttendanceStatus.WORKING);

    private final CompanySettingsService companySettings;
    private final LeaveRequestRepository leaveRequests;
    private final CompanyEventRepository companyEvents;
    private final AttendanceRepository attendance;

    /** An employee's effective schedule after applying company defaults. */
    public record EffectiveSchedule(PayBasis payBasis, int workingDaysMask, BigDecimal hoursPerDay) {}

    /** Indicative rate derivation for one month (no leave/attendance applied). */
    public record Derivation(
            EffectiveSchedule schedule,
            int scheduledWorkingDays,
            BigDecimal dailyRate,   // money-scaled, indicative
            BigDecimal hourlyRate   // money-scaled, indicative
    ) {}

    /** The paid basic for a payroll period, plus the transparency figures for the payslip. */
    public record Compensation(
            PayBasis payBasis,
            int workingDaysMask,
            BigDecimal hoursPerDay,
            int scheduledWorkingDays,
            BigDecimal dailyRate,        // money-scaled
            BigDecimal hourlyRate,       // money-scaled
            BigDecimal unpaidDays,       // day-scaled fraction (2dp) — half days and hours count as part days
            BigDecimal unpaidDeduction,  // money-scaled
            Integer paidDays,            // null for HOURLY
            BigDecimal paidBasic         // money-scaled — feeds PayrollCalculator
    ) {}

    // ---------------- schedule resolution + rate derivation ----------------

    public EffectiveSchedule resolveSchedule(Employee e) {
        CompanySettings s = companySettings.resolve(e.getCompany());
        PayBasis payBasis = e.getPayBasis() != null ? e.getPayBasis() : s.getDefaultPayBasis();
        int mask = e.getWorkingDays() != null ? e.getWorkingDays() : s.getDefaultWorkingDays();
        BigDecimal hours = e.getHoursPerDay() != null ? e.getHoursPerDay() : s.getDefaultHoursPerDay();
        return new EffectiveSchedule(payBasis, mask, hours);
    }

    /** Indicative daily/hourly derivation for {@code ym} (used on the employee detail screen). */
    public Derivation derive(Employee e, YearMonth ym) {
        EffectiveSchedule schedule = resolveSchedule(e);
        int scheduledDays = WorkWeek.scheduledDays(schedule.workingDaysMask(), ym);
        BigDecimal dailyExact = dailyRateExact(e.getMonthlySalary(), scheduledDays);
        BigDecimal hourlyExact = hourlyRateExact(dailyExact, schedule.hoursPerDay());
        return new Derivation(schedule, scheduledDays, money(dailyExact), money(hourlyExact));
    }

    // ---------------- paid basic for a payroll period ----------------

    public Compensation forPeriod(Employee e, YearMonth ym) {
        EffectiveSchedule schedule = resolveSchedule(e);
        int mask = schedule.workingDaysMask();
        BigDecimal hoursPerDay = nz(schedule.hoursPerDay());
        BigDecimal monthlySalary = nz(e.getMonthlySalary());

        int scheduledDays = WorkWeek.scheduledDays(mask, ym);
        BigDecimal dailyExact = dailyRateExact(monthlySalary, scheduledDays);
        BigDecimal hourlyExact = hourlyRateExact(dailyExact, hoursPerDay);

        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();

        // Public holidays in the period (paid non-working days).
        Set<LocalDate> holidays = new HashSet<>();
        for (CompanyEvent ev : companyEvents.findByCompanyIdAndEventTypeAndEventDateBetween(
                e.getCompany().getId(), EventType.HOLIDAY, monthStart, monthEnd)) {
            holidays.add(ev.getEventDate());
        }

        // Approved leave overlapping the period → accumulate the working-day FRACTION
        // each request consumes, per date. Partial-day leave (half day / hours) costs a
        // fraction of a day, so this can't be a whole-day count any more.
        Map<LocalDate, BigDecimal> unpaidByDate = new HashMap<>();
        Map<LocalDate, BigDecimal> paidByDate = new HashMap<>();
        for (LeaveRequest r : leaveRequests.findApprovedOverlapping(
                e.getId(), LeaveStatus.APPROVED, monthStart, monthEnd)) {
            boolean paid = r.getLeaveType().isPaid();
            BigDecimal fraction = dayFraction(r, hoursPerDay);
            if (fraction.signum() <= 0) {
                continue;
            }
            LocalDate from = r.getStartDate().isBefore(monthStart) ? monthStart : r.getStartDate();
            LocalDate to = r.getEndDate().isAfter(monthEnd) ? monthEnd : r.getEndDate();
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                if (!WorkWeek.isWorkingDay(mask, d.getDayOfWeek())) {
                    continue; // rest day — never affects pay
                }
                if (holidays.contains(d)) {
                    continue; // holiday is paid regardless; excluded from unpaid deduction
                }
                // A single date can never cost more than one day of pay, however many
                // overlapping requests land on it.
                (paid ? paidByDate : unpaidByDate).merge(d, fraction,
                        (a, b2) -> a.add(b2).min(FULL_DAY));
            }
        }
        BigDecimal unpaidDays = sum(unpaidByDate);
        BigDecimal paidLeaveDays = sum(paidByDate);

        // Attendance in the period (present days + worked minutes).
        int presentDays = 0;
        BigDecimal workedHours = BigDecimal.ZERO;
        for (AttendanceRecord rec : attendance.findByEmployeeInRange(e.getId(), monthStart, monthEnd)) {
            if (PRESENT.contains(rec.getStatus())) {
                presentDays++;
                if (rec.getWorkedMinutes() != null) {
                    workedHours = workedHours.add(
                            BigDecimal.valueOf(rec.getWorkedMinutes()).divide(new BigDecimal("60"), RATE_SCALE, RoundingMode.HALF_UP));
                }
            }
        }

        // Holidays that fall on a scheduled working day (paid non-working days).
        int holidaysOnWorkingDays = 0;
        for (LocalDate h : holidays) {
            if (WorkWeek.isWorkingDay(mask, h.getDayOfWeek())) {
                holidaysOnWorkingDays++;
            }
        }

        BigDecimal paidBasic;
        BigDecimal unpaidDeduction = BigDecimal.ZERO;
        Integer paidDays;

        switch (schedule.payBasis()) {
            case DAILY -> {
                BigDecimal paidWorkedDays = BigDecimal.valueOf(presentDays)
                        .add(paidLeaveDays)
                        .add(BigDecimal.valueOf(holidaysOnWorkingDays));
                paidBasic = money(dailyExact.multiply(paidWorkedDays));
                paidDays = wholeDays(paidWorkedDays);
            }
            case HOURLY -> {
                BigDecimal paidHours = workedHours
                        .add(paidLeaveDays.multiply(hoursPerDay))
                        .add(BigDecimal.valueOf(holidaysOnWorkingDays).multiply(hoursPerDay));
                paidBasic = money(hourlyExact.multiply(paidHours));
                paidDays = null;
            }
            default -> { // MONTHLY
                unpaidDeduction = money(dailyExact.multiply(unpaidDays));
                paidBasic = money(monthlySalary.subtract(unpaidDeduction));
                paidDays = Math.max(0, wholeDays(BigDecimal.valueOf(scheduledDays).subtract(unpaidDays)));
            }
        }

        return new Compensation(
                schedule.payBasis(), mask, hoursPerDay, scheduledDays,
                money(dailyExact), money(hourlyExact),
                unpaidDays, unpaidDeduction, paidDays, paidBasic);
    }

    // ---------------- helpers ----------------

    /**
     * The working-day fraction one approved leave request costs per qualifying date:
     * a full day is 1.00, a half day 0.50 and an hourly request {@code hours / hoursPerDay}
     * (the same 2dp fraction {@link LeaveDurationCalculator} recorded when it was applied
     * for, so the balance deduction and the pay deduction can never disagree).
     */
    private static BigDecimal dayFraction(LeaveRequest r, BigDecimal hoursPerDay) {
        LeaveDurationUnit unit = r.getDurationUnit() == null ? LeaveDurationUnit.FULL_DAY : r.getDurationUnit();
        return switch (unit) {
            case HALF_DAY -> HALF_DAY;
            case HOURS -> r.getHours() != null
                    ? LeaveDurationCalculator.hourFraction(r.getHours(), hoursPerDay)
                    : nz(r.getDays()).setScale(DAY_SCALE, RoundingMode.HALF_UP);
            default -> FULL_DAY;
        };
    }

    private static BigDecimal sum(Map<LocalDate, BigDecimal> byDate) {
        BigDecimal total = BigDecimal.ZERO;
        for (BigDecimal v : byDate.values()) {
            total = total.add(v);
        }
        return total.setScale(DAY_SCALE, RoundingMode.HALF_UP);
    }

    /** Payslip day counts are whole numbers; the exact money uses the fraction above. */
    private static int wholeDays(BigDecimal v) {
        return v.setScale(0, RoundingMode.HALF_UP).intValue();
    }

    private static BigDecimal dailyRateExact(BigDecimal monthlySalary, int scheduledDays) {
        BigDecimal salary = nz(monthlySalary);
        if (scheduledDays <= 0 || salary.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return salary.divide(BigDecimal.valueOf(scheduledDays), RATE_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal hourlyRateExact(BigDecimal dailyExact, BigDecimal hoursPerDay) {
        if (dailyExact.signum() <= 0 || hoursPerDay == null || hoursPerDay.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        return dailyExact.divide(hoursPerDay, RATE_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal money(BigDecimal v) {
        return nz(v).setScale(MONEY_SCALE, RoundingMode.HALF_UP);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v == null ? BigDecimal.ZERO : v;
    }
}
