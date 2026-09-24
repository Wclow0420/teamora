package com.teamora.leave;

import com.teamora.calendar.CompanyEvent;
import com.teamora.calendar.CompanyEventRepository;
import com.teamora.calendar.EventType;
import com.teamora.common.WorkWeek;
import com.teamora.common.exception.BadRequestException;
import com.teamora.employee.Employee;
import com.teamora.payroll.CompensationService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

/**
 * Turns a leave request into the <b>working-day fraction</b> it actually consumes.
 *
 * <p>This is the single source of truth for "how many days is this leave?", and it is
 * money-critical: the fraction it returns is both deducted from the employee's balance
 * on approval and priced by payroll ({@code dailyRate x unpaidFraction}).
 *
 * <p>Rules:
 * <ul>
 *   <li><b>FULL_DAY</b> — 1.00 for each <i>scheduled working day</i> in
 *       {@code [startDate, endDate]}. Rest days (per the employee's work-week mask)
 *       and company {@code HOLIDAY} events are skipped, so a Fri–Mon request over a
 *       Mon–Fri week costs 2 days, not 4.</li>
 *   <li><b>HALF_DAY</b> — 0.50 on a single working date.</li>
 *   <li><b>HOURS</b> — {@code hours / hoursPerDay} rounded to 2dp, on a single working
 *       date, with {@code 0 < hours <= hoursPerDay}.</li>
 * </ul>
 *
 * <p>The work-week mask and hours-per-day come from
 * {@link CompensationService#resolveSchedule(Employee)} (employee override else company
 * default) — deliberately the same resolution payroll uses, so a leave day and a pay day
 * can never disagree.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaveDurationCalculator {

    /** Days are tracked to 2dp everywhere (balances, requests, payslips). */
    public static final int DAY_SCALE = 2;

    /** A sane upper bound on a single request, so a typo can't scan years of calendar. */
    private static final int MAX_RANGE_DAYS = 366;

    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final CompensationService compensation;
    private final CompanyEventRepository companyEvents;

    /** The resolved duration of a request: its day fraction plus the normalised inputs. */
    public record Duration(BigDecimal days, LeaveDurationUnit unit, HalfDayPeriod halfDayPeriod, BigDecimal hours) {}

    /**
     * Validate and price a leave request.
     *
     * @throws BadRequestException with a user-facing message when the request can't be honoured
     */
    public Duration resolve(Employee employee,
                            LeaveDurationUnit unitOrNull,
                            LocalDate startDate,
                            LocalDate endDate,
                            HalfDayPeriod halfDayPeriod,
                            BigDecimal hours) {
        LeaveDurationUnit unit = unitOrNull == null ? LeaveDurationUnit.FULL_DAY : unitOrNull;

        if (startDate == null || endDate == null) {
            throw new BadRequestException("Pick the dates for your leave");
        }
        if (endDate.isBefore(startDate)) {
            throw new BadRequestException("End date cannot be before start date");
        }
        if (endDate.toEpochDay() - startDate.toEpochDay() + 1 > MAX_RANGE_DAYS) {
            throw new BadRequestException("A single leave request can't be longer than a year");
        }

        CompensationService.EffectiveSchedule schedule = compensation.resolveSchedule(employee);
        int mask = schedule.workingDaysMask();
        Set<LocalDate> holidays = holidays(employee, startDate, endDate);

        return switch (unit) {
            case HALF_DAY -> {
                requireSingleDate(startDate, endDate, "A half-day leave");
                requireWorkingDay(startDate, mask, holidays);
                if (halfDayPeriod == null) {
                    throw new BadRequestException("Choose the morning (AM) or afternoon (PM) for a half-day leave");
                }
                yield new Duration(days("0.5"), unit, halfDayPeriod, null);
            }
            case HOURS -> {
                requireSingleDate(startDate, endDate, "An hourly leave");
                requireWorkingDay(startDate, mask, holidays);
                BigDecimal hoursPerDay = schedule.hoursPerDay();
                if (hoursPerDay == null || hoursPerDay.signum() <= 0) {
                    throw new BadRequestException("Your working hours aren't set up yet — ask HR to add them");
                }
                if (hours == null || hours.signum() <= 0) {
                    throw new BadRequestException("Enter how many hours of leave you need");
                }
                if (hours.compareTo(hoursPerDay) > 0) {
                    throw new BadRequestException(
                            "That's more than a working day (" + plain(hoursPerDay) + " hours) — apply for a full day instead");
                }
                yield new Duration(hourFraction(hours, hoursPerDay), unit, null,
                        hours.setScale(DAY_SCALE, RoundingMode.HALF_UP));
            }
            default -> {
                int workingDays = 0;
                for (LocalDate d = startDate; !d.isAfter(endDate); d = d.plusDays(1)) {
                    if (WorkWeek.isWorkingDay(mask, d.getDayOfWeek()) && !holidays.contains(d)) {
                        workingDays++;
                    }
                }
                if (workingDays == 0) {
                    throw new BadRequestException(
                            "There are no working days in that range — public holidays and rest days don't use up your leave");
                }
                yield new Duration(days(String.valueOf(workingDays)), LeaveDurationUnit.FULL_DAY, null, null);
            }
        };
    }

    /**
     * The day fraction for {@code hours} out of a full working day, at 2dp.
     * Shared with payroll so the balance deduction and the pay deduction agree.
     */
    public static BigDecimal hourFraction(BigDecimal hours, BigDecimal hoursPerDay) {
        if (hours == null || hoursPerDay == null || hoursPerDay.signum() <= 0) {
            return BigDecimal.ZERO.setScale(DAY_SCALE);
        }
        return hours.divide(hoursPerDay, DAY_SCALE, RoundingMode.HALF_UP);
    }

    // ---------------- helpers ----------------

    private Set<LocalDate> holidays(Employee employee, LocalDate from, LocalDate to) {
        Set<LocalDate> dates = new HashSet<>();
        for (CompanyEvent ev : companyEvents.findByCompanyIdAndEventTypeAndEventDateBetween(
                employee.getCompany().getId(), EventType.HOLIDAY, from, to)) {
            dates.add(ev.getEventDate());
        }
        return dates;
    }

    private static void requireSingleDate(LocalDate start, LocalDate end, String what) {
        if (!start.equals(end)) {
            throw new BadRequestException(what + " covers one date only — set the same start and end date");
        }
    }

    private static void requireWorkingDay(LocalDate date, int mask, Set<LocalDate> holidays) {
        if (!WorkWeek.isWorkingDay(mask, date.getDayOfWeek())) {
            throw new BadRequestException(date.format(DATE) + " isn't one of your working days");
        }
        if (holidays.contains(date)) {
            throw new BadRequestException(date.format(DATE) + " is a public holiday — you don't need to apply for leave");
        }
    }

    private static BigDecimal days(String v) {
        return new BigDecimal(v).setScale(DAY_SCALE, RoundingMode.HALF_UP);
    }

    private static String plain(BigDecimal v) {
        return v.stripTrailingZeros().toPlainString();
    }
}
