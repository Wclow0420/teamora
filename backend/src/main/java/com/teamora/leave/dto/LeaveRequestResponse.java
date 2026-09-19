package com.teamora.leave.dto;

import com.teamora.leave.LeaveRequest;
import com.teamora.leave.LeaveStatus;
import com.teamora.leave.LeaveType;

import java.util.UUID;

/** A leave request row for the staff Leave screen list. */
public record LeaveRequestResponse(
        UUID id,
        LeaveType type,
        String typeLabel,
        String dateRangeLabel,
        String durationLabel,
        String reason,
        LeaveStatus status,
        String statusLabel
) {
    public static LeaveRequestResponse from(LeaveRequest r) {
        return new LeaveRequestResponse(
                r.getId(),
                r.getLeaveType(),
                r.getLeaveType().label(),
                LeaveLabels.dateRange(r.getStartDate(), r.getEndDate()),
                LeaveLabels.duration(r.getDays() == null ? 0 : r.getDays()),
                r.getReason(),
                r.getStatus(),
                r.getStatus().label());
    }
}
