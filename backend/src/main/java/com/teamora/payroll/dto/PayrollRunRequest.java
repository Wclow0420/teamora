package com.teamora.payroll.dto;

import jakarta.validation.constraints.NotBlank;

/** Request body for triggering a payroll run for a period (YYYY-MM). */
public record PayrollRunRequest(
        @NotBlank String period
) {}
