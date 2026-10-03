package com.teamora.audit;

/** Sensitive admin actions recorded in {@code audit_events}. */
public enum AuditAction {
    BANK_DETAILS_CHANGED,
    SALARY_CHANGED,
    ROLE_CHANGED,
    OWNERSHIP_TRANSFERRED,
    PASSWORD_RESET_BY_ADMIN,
    EMPLOYEE_DEACTIVATED,
    EMPLOYEE_REACTIVATED,
    PAYROLL_APPROVED,
    PAYROLL_MARKED_PAID
}
