package com.teamora.leave.dto;

import com.teamora.leave.HalfDayPeriod;
import com.teamora.leave.LeaveDurationUnit;
import com.teamora.leave.LeaveRequest;
import com.teamora.leave.LeaveStatus;
import com.teamora.leave.LeaveType;

import java.math.BigDecimal;
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
        LeaveDurationUnit durationUnit,
        HalfDayPeriod halfDayPeriod,
        String hoursLabel,
        BigDecimal days,
        String reason,
        LeaveStatus status,
        String statusLabel
) {
    public static LeaveRequestResponse from(LeaveRequest r) {
        LeaveType t = r.getLeaveType();
        LeaveDurationUnit unit = r.getDurationUnit() == null ? LeaveDurationUnit.FULL_DAY : r.getDurationUnit();
        return new LeaveRequestResponse(
                r.getId(),
                t.getId(),
                t.getCode(),
                t.getName(),
                t.getColorKey(),
                t.isPaid(),
                LeaveLabels.dateRange(r.getStartDate(), r.getEndDate()),
                LeaveLabels.duration(r.getDays(), unit, r.getHalfDayPeriod(), r.getHours()),
                unit,
                r.getHalfDayPeriod(),
                LeaveLabels.hoursLabel(r.getHours()),
                r.getDays(),
                r.getReason(),
                r.getStatus(),
                r.getStatus().label());
    }
}
