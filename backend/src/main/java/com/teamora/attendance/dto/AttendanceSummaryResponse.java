package com.teamora.attendance.dto;

import java.util.List;

/** Payload for the staff Attendance screen: monthly stats + weekly chart + history rows. */
public record AttendanceSummaryResponse(
        String month,
        int present,
        int late,
        int leave,
        String otHoursLabel,
        double[] weeklyHours,
        String weeklyTotalLabel,
        List<AttendanceDayResponse> days
) {
}
