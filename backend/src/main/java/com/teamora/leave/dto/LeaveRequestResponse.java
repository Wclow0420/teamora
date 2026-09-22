package com.teamora.leave.dto;

import com.teamora.leave.LeaveRequest;
import com.teamora.leave.LeaveStatus;
import com.teamora.leave.LeaveType;

import java.util.UUID;

/** A leave request row for the staff Leave screen list. */
public record LeaveRequestResponse(
        UUID id,
        UUID leaveTypeId,
        String typeCode,
        String typeLabel,
        String colorKey,
        boolean paid,
        String dateRangeLabel,
        String durationLabel,
        String reason,
        LeaveStatus status,
        String statusLabel
) {
    public static LeaveRequestResponse from(LeaveRequest r) {
        LeaveType t = r.getLeaveType();
        return new LeaveRequestResponse(
                r.getId(),
                t.getId(),
                t.getCode(),
                t.getName(),
                t.getColorKey(),
                t.isPaid(),
                LeaveLabels.dateRange(r.getStartDate(), r.getEndDate()),
                LeaveLabels.duration(r.getDays() == null ? 0 : r.getDays()),
                r.getReason(),
                r.getStatus(),
                r.getStatus().label());
    }
}
