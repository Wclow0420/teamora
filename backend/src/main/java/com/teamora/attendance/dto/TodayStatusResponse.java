package com.teamora.attendance.dto;

import com.teamora.attendance.AttendanceRecord;
import com.teamora.attendance.AttendanceStatus;

import java.time.Instant;

/** Home hero: where the current employee stands today. */
public record TodayStatusResponse(
        AttendanceStatus status,
        Instant clockInAt,
        Integer workedMinutes,
        String shift,
        String location
) {
    private static final String DEFAULT_SHIFT = "9:00 AM – 6:00 PM";

    public static TodayStatusResponse from(AttendanceRecord r) {
        if (r == null) {
            return new TodayStatusResponse(AttendanceStatus.ABSENT, null, null, DEFAULT_SHIFT, null);
        }
        return new TodayStatusResponse(
                r.getStatus(),
                r.getClockInAt(),
                r.getWorkedMinutes(),
                DEFAULT_SHIFT,
                r.getLocation()
        );
    }
}
