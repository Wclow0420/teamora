package com.teamora.leave.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Set one employee's entitlement for a leave type in a leave year (seniority-based
 * leave, negotiated packages, corrections). {@code leaveYear} defaults to the
 * company's current leave year.
 */
public record OverrideLeaveBalanceRequest(
        @NotNull UUID employeeId,
        @NotNull UUID leaveTypeId,
        @Min(2000) @Max(2999) Integer leaveYear,
        @NotNull @PositiveOrZero BigDecimal entitled
) {}
