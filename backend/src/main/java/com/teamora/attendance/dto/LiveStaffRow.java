package com.teamora.attendance.dto;

import com.teamora.attendance.AttendanceRecord;
import com.teamora.attendance.AttendanceStatus;
import com.teamora.employee.Employee;

/** One person's live status on the admin Live board. */
public record LiveStaffRow(
        java.util.UUID employeeId,
        String name,
        String initial,
        String department,
        AttendanceStatus status,
        String time,
        String accentColorKey
) {
    public static LiveStaffRow from(Employee e, AttendanceRecord r) {
        AttendanceStatus status = r != null ? r.getStatus() : AttendanceStatus.ABSENT;
        String time = timeLabel(status, r);
        return new LiveStaffRow(
                e.getId(),
                e.getFullName(),
                e.getInitial(),
                e.getDepartment(),
                status,
                time,
                accentKey(status)
        );
    }

    private static String timeLabel(AttendanceStatus status, AttendanceRecord r) {
        return switch (status) {
            case ON_LEAVE -> "On leave";
            case ABSENT -> "Not in";
            default -> {
                if (r == null || r.getClockInAt() == null) {
                    yield "—";
                }
                yield "In " + AttendanceDayResponse.formatTime(r.getClockInAt());
            }
        };
    }

    /** "sage"|"violet"|"amber"|"coral" derived from status. */
    static String accentKey(AttendanceStatus status) {
        return switch (status) {
            case PRESENT, WORKING -> "sage";
            case REMOTE -> "violet";
            case LATE -> "amber";
            case ON_LEAVE, ABSENT -> "coral";
        };
    }
}
