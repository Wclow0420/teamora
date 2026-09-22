package com.teamora.payroll.dto;

import java.util.List;

/**
 * On-screen statutory contribution summary for one payroll period. Every money value is
 * a pre-formatted {@code *Label} (grouped, 2dp) so the app renders it verbatim — matching
 * the label convention used across the payroll DTOs.
 *
 * <p>Employee-side amounts (EPF/SOCSO/EIS employee, PCB, net) come straight from the stored
 * payslip — never recomputed. Employer-side amounts are not persisted on the payslip, so they
 * are derived deterministically from the stored basic + bonus via {@code PayrollCalculator}
 * (the same pure function used at run time), keeping them consistent with the employee side.
 */
public record StatutorySummaryResponse(
        String period,
        String periodLabel,
        String generatedAtLabel,
        List<Row> rows,
        Totals totals
) {
    /** One employee's statutory line. Identity fields are null/blank when unset (honest empty state). */
    public record Row(
            String employeeName,
            String staffId,
            String nric,
            String epfNo,
            String epfEmployeeLabel,
            String epfEmployerLabel,
            String socsoNo,
            String socsoEmployeeLabel,
            String socsoEmployerLabel,
            String eisEmployeeLabel,
            String eisEmployerLabel,
            String taxNo,
            String pcbLabel,
            String netLabel
    ) {}

    /** Company totals — equal to the sum of the rows, to the sen. */
    public record Totals(
            String epfEmployeeLabel,
            String epfEmployerLabel,
            String socsoEmployeeLabel,
            String socsoEmployerLabel,
            String eisEmployeeLabel,
            String eisEmployerLabel,
            String pcbLabel,
            String netLabel
    ) {}
}
