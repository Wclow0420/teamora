package com.teamora.attendance.dto;

import com.teamora.attendance.AttendanceRecord;
import com.teamora.attendance.AttendanceStatus;

import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.Locale;

/** One row of the staff Attendance history list. */
public record AttendanceDayResponse(
        LocalDate date,
        String weekday,
        String clockIn,
        String clockOut,
        AttendanceStatus status,
        String statusLabel,
        String workedLabel,
        boolean live
) {
    private static final ZoneId KL = ZoneId.of("Asia/Kuala_Lumpur");
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    public static AttendanceDayResponse from(AttendanceRecord r) {
        boolean live = r.getStatus() == AttendanceStatus.WORKING
                && r.getClockInAt() != null && r.getClockOutAt() == null;
        return new AttendanceDayResponse(
                r.getWorkDate(),
                r.getWorkDate().getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                formatTime(r.getClockInAt()),
                formatTime(r.getClockOutAt()),
                r.getStatus(),
                statusLabel(r.getStatus()),
                workedLabel(r.getWorkedMinutes()),
                live
        );
    }

    static String formatTime(Instant instant) {
        if (instant == null) {
            return null;
        }
        LocalTime t = instant.atZone(KL).toLocalTime();
        return TIME.format(t);
    }

    static String statusLabel(AttendanceStatus status) {
        return switch (status) {
            case WORKING -> "Working";
            case PRESENT -> "On time";
            case LATE -> "Late";
            case REMOTE -> "Remote";
            case ON_LEAVE -> "On leave";
            case ABSENT -> "Absent";
        };
    }

    /** e.g. 552 → "9h 12m". Null/zero → "—". */
    static String workedLabel(Integer minutes) {
        if (minutes == null || minutes <= 0) {
            return "—";
        }
        int h = minutes / 60;
        int m = minutes % 60;
        if (h == 0) {
            return m + "m";
        }
        return h + "h " + m + "m";
    }
}
