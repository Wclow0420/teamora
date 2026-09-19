package com.teamora.payroll;

/** Lifecycle of a payroll run / payslip. Maps to the {@code payslips.status} column. */
public enum PayslipStatus {
    DRAFT,
    IN_REVIEW,
    APPROVED,
    PAID
}
