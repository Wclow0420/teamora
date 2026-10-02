package com.teamora.attendance.dto;

import com.teamora.attendance.AttendanceRecord;
import com.teamora.attendance.AttendanceStatus;

import java.time.Instant;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Home hero: where the current employee stands today. */
public record TodayStatusResponse(
        AttendanceStatus status,
        Instant clockInAt,
        Integer workedMinutes,
        String shift,
        String location,
        /** Minutes spent clocked out mid-day (0 when none). Worked time is net of this. */
        int breakMinutes,
        /** When the employee last clocked out today; null while working / not clocked in. */
        Instant clockOutAt
) {
    private static final DateTimeFormatter H_MM_A = DateTimeFormatter.ofPattern("h:mm a", Locale.ENGLISH);

    /**
     * @param workStart the company's configured work start time — the only shift
     *                  fact we actually know, so the label is "starts 9:00 AM"
     *                  rather than an invented start–end range.
     */
    public static TodayStatusResponse from(AttendanceRecord r, LocalTime workStart) {
        String shift = workStart == null ? null : "starts " + workStart.format(H_MM_A);
        if (r == null) {
            return new TodayStatusResponse(AttendanceStatus.ABSENT, null, null, shift, null, 0, null);
        }
        return new TodayStatusResponse(
                r.getStatus(),
                r.getClockInAt(),
                r.getWorkedMinutes(),
                shift,
                r.getLocation(),
                r.getBreakMinutes(),
                r.getClockOutAt()
        );
    }
}
