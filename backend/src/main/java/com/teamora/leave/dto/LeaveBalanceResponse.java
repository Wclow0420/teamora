package com.teamora.leave.dto;

import com.teamora.leave.LeaveBalance;
import com.teamora.leave.LeaveType;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A single leave-type balance tile for the staff Leave screen.
 *
 * <p>{@code used} / {@code entitled} / {@code remaining} stay JSON numbers but are now
 * fractional (2dp) because half-day and hourly leave consume part of a day.
 */
public record LeaveBalanceResponse(
        UUID leaveTypeId,
        String code,
        String name,
        String colorKey,
        boolean paid,
        BigDecimal used,
        BigDecimal entitled,
        BigDecimal remaining,
        String usedLabel,
        String remainingLabel
) {
    public static LeaveBalanceResponse from(LeaveBalance b) {
        LeaveType t = b.getLeaveType();
        BigDecimal used = b.usedOrZero();
        BigDecimal entitled = b.entitledOrZero();
        BigDecimal remaining = b.remaining();
        return new LeaveBalanceResponse(
                t.getId(),
                t.getCode(),
                t.getName(),
                t.getColorKey(),
                t.isPaid(),
                used,
                entitled,
                remaining,
                LeaveLabels.plain(used) + " / " + LeaveLabels.plain(entitled),
                LeaveLabels.dayLabel(remaining) + " left");
    }
}
