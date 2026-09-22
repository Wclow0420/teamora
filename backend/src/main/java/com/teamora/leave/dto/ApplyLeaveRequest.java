package com.teamora.leave.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.UUID;

/** Payload to apply for leave. */
public record ApplyLeaveRequest(
        @NotNull UUID leaveTypeId,
        @NotNull LocalDate startDate,
        @NotNull LocalDate endDate,
        @Size(max = 500) String reason
) {
}
