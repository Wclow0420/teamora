package com.teamora.payroll.dto;

import com.teamora.payroll.PayslipStatus;

import java.math.BigDecimal;

/** Admin Payroll screen projection — aggregate of one payroll run. */
public record PayrollSummaryResponse(
        String period,
        String periodLabel,
        int employeeCount,
        String grossLabel,
        String epfLabel,
        String socsoEisLabel,
        String netLabel,
        BigDecimal net,
        String payDateLabel,
        PayslipStatus status,
        String statusLabel
) {}
