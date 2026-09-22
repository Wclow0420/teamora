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
        String accentColorKey,
        /** Today's attendance record id (null if the employee hasn't clocked in). */
        java.util.UUID attendanceRecordId,
        /** True when that record has a stored clock-in selfie — the app builds the photo URL. */
        boolean hasPhoto
) {
    public static LiveStaffRow from(Employee e, AttendanceRecord r) {
        AttendanceStatus status = r != null ? r.getStatus() : AttendanceStatus.ABSENT;
        String time = timeLabel(status, r);
        // Derive hasPhoto from the (always-loaded) type column, not the bytes.
        boolean hasPhoto = r != null && r.getClockInPhotoType() != null;
        return new LiveStaffRow(
                e.getId(),
                e.getFullName(),
                e.getInitial(),
                e.getDepartment(),
                status,
                time,
                accentKey(status),
                r != null ? r.getId() : null,
                hasPhoto
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
