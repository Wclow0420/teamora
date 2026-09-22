package com.teamora.leave.dto;

import com.teamora.employee.Employee;
import com.teamora.leave.LeaveRequest;
import com.teamora.leave.LeaveType;

import java.util.UUID;

/** A pending leave request enriched for the admin Approvals screen. */
public record PendingLeaveResponse(
        UUID id,
        String employeeName,
        String initial,
        UUID leaveTypeId,
        String typeCode,
        String typeLabel,
        boolean paid,
        String dateRangeLabel,
        String durationLabel,
        String reason,
        String balanceLabel,
        String accentColorKey
) {
    /**
     * @param remaining days left in the employee's balance for this leave type,
     *                  or {@code null} when no balance is on record.
     */
    public static PendingLeaveResponse from(LeaveRequest r, Integer remaining) {
        Employee e = r.getEmployee();
        LeaveType t = r.getLeaveType();
        String balanceLabel = remaining == null
                ? "No balance"
                : remaining + (remaining == 1 ? " day left" : " days left");
        return new PendingLeaveResponse(
                r.getId(),
                e.getFullName(),
                e.getInitial(),
                t.getId(),
                t.getCode(),
                t.getName(),
                t.isPaid(),
                LeaveLabels.dateRange(r.getStartDate(), r.getEndDate()),
                LeaveLabels.duration(r.getDays() == null ? 0 : r.getDays()),
                r.getReason(),
                balanceLabel,
                t.getColorKey());
    }
}
