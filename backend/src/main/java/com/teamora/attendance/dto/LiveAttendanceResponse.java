package com.teamora.attendance.dto;

import java.util.List;

/** Admin Live screen: aggregate counts + per-person rows. */
public record LiveAttendanceResponse(
        Counts counts,
        List<LiveStaffRow> staff
) {
    public record Counts(
            int inOffice,
            int remote,
            int late,
            int out
    ) {
    }
}
