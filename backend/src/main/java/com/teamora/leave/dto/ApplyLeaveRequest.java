package com.teamora.leave.dto;

import com.teamora.leave.HalfDayPeriod;
import com.teamora.leave.LeaveDurationUnit;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/**
 * Payload to apply for leave.
 *
 * <p>{@code durationUnit} is optional and defaults to {@link LeaveDurationUnit#FULL_DAY},
 * so older clients that only send a date range keep working. HALF_DAY and HOURS cover a
 * single date, so {@code startDate} must equal {@code endDate} for those — the range,
 * hours and working-day rules are validated by the leave duration calculator, which
 * returns a friendly 400.
 */
public record ApplyLeaveRequest(
        @NotNull UUID leaveTypeId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        LeaveDurationUnit durationUnit,
        HalfDayPeriod halfDayPeriod,
        BigDecimal hours,
        LocalTime startTime,
        @Size(max = 500) String reason
) {
    /** The requested unit, defaulting to a full day when the client omits it. */
    public LeaveDurationUnit unit() {
        return durationUnit == null ? LeaveDurationUnit.FULL_DAY : durationUnit;
    }
}
