package com.teamora.employee;

/**
 * System role within a company (best-practice HR hierarchy). Schema is designed
 * so custom per-company roles/permissions can be layered on later.
 *
 *  OWNER     — created the company; full control incl. billing.
 *  HR_ADMIN  — manages people, payroll, approvals.
 *  MANAGER   — approves their team's requests; sees the admin views.
 *  EMPLOYEE  — the staff app.
 *
 * Mapped to Spring authority "ROLE_<name>".
 */
public enum Role {
    OWNER,
    HR_ADMIN,
    MANAGER,
    EMPLOYEE;

    public String authority() {
        return "ROLE_" + name();
    }

    /** Roles that get the admin/manager experience + /api/admin access. */
    public boolean isManagement() {
        return this != EMPLOYEE;
    }

    /** Roles allowed to manage employees + assign roles. */
    public boolean canManageEmployees() {
        return this == OWNER || this == HR_ADMIN;
    }
}
