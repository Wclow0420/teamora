package com.teamora.leave.dto;

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

    /** Duration label, e.g. "1 day" / "2 days". */
    static String duration(int days) {
        return days + (days == 1 ? " day" : " days");
    }

    private static String day(LocalDate d) {
        return String.valueOf(d.getDayOfMonth());
    }

    private static String month(LocalDate d) {
        return d.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    }
}
