package com.teamora.payroll.dto;

import com.teamora.payroll.PayslipStatus;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

/**
 * The admin Payroll screen's single source of truth for one period: the run-level
 * totals + lifecycle status, plus a per-employee breakdown. {@code generated} is
 * false when no payroll has been run for the period yet.
 */
public record PayrollRunResponse(
        String period,
        String periodLabel,
        boolean generated,
        int employeeCount,
        int skippedCount,
        String grossLabel,
        String statutoryLabel,
        String pcbLabel,
        String netLabel,
        BigDecimal net,
        String payDateLabel,
        PayslipStatus status,
        String statusLabel,
        List<Line> lines
) {
    /** One employee's line in the run. */
    public record Line(
            UUID payslipId,
            String employeeName,
            String initial,
            String department,
            String basicLabel,
            String overtimeLabel,
            String claimsLabel,
            String grossLabel,
            String epfLabel,
            String socsoLabel,
            String eisLabel,
            String pcbLabel,
            String deductionsLabel,
            String netLabel,
            PayslipStatus status
    ) {}
}
