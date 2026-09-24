package com.teamora.leave.dto;

import com.teamora.employee.Employee;
import com.teamora.leave.HalfDayPeriod;
import com.teamora.leave.LeaveDurationUnit;
import com.teamora.leave.LeaveRequest;
import com.teamora.leave.LeaveType;

import java.math.BigDecimal;
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
        LeaveDurationUnit durationUnit,
        HalfDayPeriod halfDayPeriod,
        String hoursLabel,
        BigDecimal days,
        String reason,
        String balanceLabel,
        String accentColorKey
) {
    /**
     * @param remaining days left in the employee's balance for this leave type (may be
     *                  fractional), or {@code null} when no balance is on record.
     */
    public static PendingLeaveResponse from(LeaveRequest r, BigDecimal remaining) {
        Employee e = r.getEmployee();
        LeaveType t = r.getLeaveType();
        LeaveDurationUnit unit = r.getDurationUnit() == null ? LeaveDurationUnit.FULL_DAY : r.getDurationUnit();
        String balanceLabel = remaining == null
                ? "No balance"
                : LeaveLabels.dayLabel(remaining) + " left";
        return new PendingLeaveResponse(
                r.getId(),
                e.getFullName(),
                e.getInitial(),
                t.getId(),
                t.getCode(),
                t.getName(),
                t.isPaid(),
                LeaveLabels.dateRange(r.getStartDate(), r.getEndDate()),
                LeaveLabels.duration(r.getDays(), unit, r.getHalfDayPeriod(), r.getHours()),
                unit,
                r.getHalfDayPeriod(),
                LeaveLabels.hoursLabel(r.getHours()),
                r.getDays(),
                r.getReason(),
                balanceLabel,
                t.getColorKey());
    }
}
