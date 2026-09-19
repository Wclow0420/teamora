package com.teamora.leave.dto;

import com.teamora.leave.LeaveBalance;
import com.teamora.leave.LeaveType;

/** A single leave-type balance tile for the staff Leave screen. */
public record LeaveBalanceResponse(
        LeaveType type,
        String label,
        int used,
        int entitled,
        int remaining
) {
    public static LeaveBalanceResponse from(LeaveBalance b) {
        return new LeaveBalanceResponse(
                b.getLeaveType(),
                b.getLeaveType().shortLabel(),
                b.getUsed() == null ? 0 : b.getUsed(),
                b.getEntitled() == null ? 0 : b.getEntitled(),
                b.remaining());
    }
}
