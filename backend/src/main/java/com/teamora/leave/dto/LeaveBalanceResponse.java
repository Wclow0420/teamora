package com.teamora.leave.dto;

import com.teamora.leave.LeaveAccrual;
import com.teamora.leave.LeaveBalance;
import com.teamora.leave.LeaveType;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * A single leave-type balance tile for the staff Leave screen, scoped to one leave year.
 *
 * <p>{@code used} / {@code entitled} / {@code remaining} stay JSON numbers but are
 * fractional (2dp) because half-day and hourly leave consume part of a day.
 * {@code remaining} is the <b>available</b> figure the app already renders:
 * {@code accruedToDate + carriedForward − used}. It can be negative when an admin
 * lowers an entitlement after leave was taken.
 */
public record LeaveBalanceResponse(
        UUID leaveTypeId,
        String code,
        String name,
        String colorKey,
        boolean paid,
        LeaveAccrual accrual,
        int leaveYear,
        BigDecimal used,
        BigDecimal entitled,
        BigDecimal accruedToDate,
        BigDecimal carriedForward,
        BigDecimal remaining,
        String usedLabel,
        String remainingLabel
) {
    public static LeaveBalanceResponse from(LeaveBalance b, int leaveYearStartMonth, LocalDate today) {
        LeaveType t = b.getLeaveType();
        BigDecimal used = b.usedOrZero();
        BigDecimal entitled = b.entitledOrZero();
        BigDecimal accrued = b.accruedToDate(leaveYearStartMonth, today);
        BigDecimal carried = b.carriedForwardOrZero();
        BigDecimal available = b.available(leaveYearStartMonth, today);
        return new LeaveBalanceResponse(
                t.getId(),
                t.getCode(),
                t.getName(),
                t.getColorKey(),
                t.isPaid(),
                t.getAccrual(),
                b.getLeaveYear(),
                used,
                entitled,
                accrued,
                carried,
                available,
                LeaveLabels.plain(used) + " / " + LeaveLabels.plain(entitled),
                LeaveLabels.dayLabel(available) + " left");
    }
}
