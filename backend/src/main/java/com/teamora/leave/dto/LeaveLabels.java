package com.teamora.leave.dto;

import com.teamora.leave.HalfDayPeriod;
import com.teamora.leave.LeaveDurationUnit;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;

/** Small formatting helpers shared by the leave request DTOs. */
final class LeaveLabels {

    private LeaveLabels() {
    }

    /**
     * Compact, human range label, e.g. "18–19 Jun", "10 Jun", or
     * "30 Jun – 2 Jul" when the months differ. Uses an en dash.
     */
    static String dateRange(LocalDate start, LocalDate end) {
        if (start == null) {
            return "";
        }
        if (end == null || end.equals(start)) {
            return day(start) + " " + month(start);
        }
        if (start.getMonth() == end.getMonth() && start.getYear() == end.getYear()) {
            return day(start) + "–" + day(end) + " " + month(start);
        }
        return day(start) + " " + month(start) + " – " + day(end) + " " + month(end);
    }

    /**
     * Duration label for a request: "Half day (AM)", "2 hours", "1 day", "3 days".
     * Falls back to the day count when the unit is missing or inconsistent.
     */
    static String duration(BigDecimal days, LeaveDurationUnit unit, HalfDayPeriod period, BigDecimal hours) {
        if (unit == LeaveDurationUnit.HALF_DAY) {
            return period == null ? "Half day" : "Half day (" + period.label() + ")";
        }
        if (unit == LeaveDurationUnit.HOURS && hours != null && hours.signum() > 0) {
            return hoursLabel(hours);
        }
        return dayLabel(days);
    }

    /** "2 hours" / "1 hour" / "1.5 hours". Null → null. */
    static String hoursLabel(BigDecimal hours) {
        if (hours == null || hours.signum() <= 0) {
            return null;
        }
        return plain(hours) + (isOne(hours) ? " hour" : " hours");
    }

    /** "1 day" / "3 days" / "0.5 days". Null → "0 days". */
    static String dayLabel(BigDecimal days) {
        BigDecimal d = days == null ? BigDecimal.ZERO : days;
        return plain(d) + (isOne(d) ? " day" : " days");
    }

    /** A day count without trailing zeros: 3.00 → "3", 0.50 → "0.5". */
    static String plain(BigDecimal v) {
        if (v == null) {
            return "0";
        }
        BigDecimal stripped = v.stripTrailingZeros();
        return (stripped.scale() < 0 ? stripped.setScale(0) : stripped).toPlainString();
    }

    private static boolean isOne(BigDecimal v) {
        return v.compareTo(BigDecimal.ONE) == 0;
    }

    private static String day(LocalDate d) {
        return String.valueOf(d.getDayOfMonth());
    }

    private static String month(LocalDate d) {
        return d.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    }
}
