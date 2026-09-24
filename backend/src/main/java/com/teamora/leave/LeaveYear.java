package com.teamora.leave;

import java.time.LocalDate;

/**
 * The company's leave year — a 12-month window that starts on the 1st of
 * {@code startMonth} and is identified by the calendar year it <b>starts</b> in.
 *
 * <p>With {@code startMonth = 1} the leave year is the calendar year. With
 * {@code startMonth = 4} the leave year <b>2026</b> runs 1 Apr 2026 → 31 Mar 2027,
 * so 15 Jan 2027 belongs to leave year 2026, not 2027.
 */
public final class LeaveYear {

    /** January — i.e. the leave year is the calendar year. */
    public static final int DEFAULT_START_MONTH = 1;

    private LeaveYear() {
    }

    /** Clamp a (possibly null or out-of-range) configured start month to a usable 1–12. */
    public static int normaliseStartMonth(Integer startMonth) {
        if (startMonth == null || startMonth < 1 || startMonth > 12) {
            return DEFAULT_START_MONTH;
        }
        return startMonth;
    }

    /** First day of leave year {@code year}. */
    public static LocalDate startOf(int year, int startMonth) {
        return LocalDate.of(year, normaliseStartMonth(startMonth), 1);
    }

    /** Last day of leave year {@code year} (start + 1 year − 1 day). */
    public static LocalDate endOf(int year, int startMonth) {
        return startOf(year, startMonth).plusYears(1).minusDays(1);
    }

    /** The leave year containing {@code date}, identified by its starting calendar year. */
    public static int yearOf(LocalDate date, int startMonth) {
        int start = normaliseStartMonth(startMonth);
        return date.getMonthValue() >= start ? date.getYear() : date.getYear() - 1;
    }

    /** 0-based position of {@code date}'s month within its leave year (0 = the start month). */
    public static int monthIndexWithinYear(LocalDate date, int startMonth) {
        int index = date.getMonthValue() - normaliseStartMonth(startMonth);
        return index < 0 ? index + 12 : index;
    }

    /** True when {@code date} falls inside leave year {@code year}. */
    public static boolean contains(int year, int startMonth, LocalDate date) {
        return yearOf(date, startMonth) == year;
    }
}
