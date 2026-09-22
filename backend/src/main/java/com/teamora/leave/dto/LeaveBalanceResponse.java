package com.teamora.leave.dto;

import com.teamora.leave.LeaveBalance;
import com.teamora.leave.LeaveType;

import java.util.UUID;

/** A single leave-type balance tile for the staff Leave screen. */
public record LeaveBalanceResponse(
        UUID leaveTypeId,
        String code,
        String name,
        String colorKey,
        boolean paid,
        int used,
        int entitled,
        int remaining
) {
    public static LeaveBalanceResponse from(LeaveBalance b) {
        LeaveType t = b.getLeaveType();
        return new LeaveBalanceResponse(
                t.getId(),
                t.getCode(),
                t.getName(),
                t.getColorKey(),
                t.isPaid(),
                b.getUsed() == null ? 0 : b.getUsed(),
                b.getEntitled() == null ? 0 : b.getEntitled(),
                b.remaining());
    }
}
