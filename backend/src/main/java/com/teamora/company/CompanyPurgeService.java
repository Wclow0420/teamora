package com.teamora.company;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

/**
 * Hard-deletes a company and every row that belongs to it, in one transaction.
 *
 * <p>Plain SQL in a fixed, FK-safe order (children before parents) — the list
 * below is every table created by the Flyway migrations (V1–V26) except
 * {@code flyway_schema_history}. <b>When a migration adds a tenant table, add it
 * here</b> (and to {@code CompanyDeletionIT}, which asserts zero rows per table).
 *
 * <p>Tables without a {@code company_id} column ({@code refresh_tokens},
 * {@code password_reset_codes}) are scoped through the company's employees.
 * Tables that have both are matched on either, so nothing tied to one of the
 * company's people survives even if its {@code company_id} were ever stale.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CompanyPurgeService {

    /** The company's employee ids, as a sub-select bound to the company id. */
    private static final String EMPLOYEES_OF = "(SELECT id FROM employees WHERE company_id = ?)";

    private final JdbcTemplate jdbc;

    /** Must run inside the caller's transaction so a failure rolls the whole purge back. */
    @Transactional(propagation = Propagation.MANDATORY)
    public void purge(UUID companyId) {
        int total = 0;
        // FK-safe order: children before parents. Employee-scoped only (no company_id column):
        total += jdbc.update("DELETE FROM password_reset_codes WHERE employee_id IN " + EMPLOYEES_OF, companyId);
        total += jdbc.update("DELETE FROM refresh_tokens WHERE employee_id IN " + EMPLOYEES_OF, companyId);
        // Audit trail (company-scoped; points at the employees being deleted):
        total += jdbc.update("DELETE FROM audit_events WHERE company_id = ? OR actor_id IN " + EMPLOYEES_OF
                + " OR target_employee_id IN " + EMPLOYEES_OF, companyId, companyId, companyId);
        // Clock-in selfies (V23) — before their attendance records:
        total += jdbc.update("DELETE FROM attendance_photos WHERE company_id = ? OR record_id IN "
                + "(SELECT id FROM attendance_records WHERE company_id = ?)", companyId, companyId);
        // Company- and employee-scoped (claims carry receipt photos;
        // leave rows go before leave_types, every one of these before employees):
        for (String table : List.of("push_tokens", "notifications", "shifts", "overtime_requests", "payslips",
                "claims", "leave_balances", "leave_requests", "attendance_records")) {
            total += jdbc.update("DELETE FROM " + table + " WHERE company_id = ? OR employee_id IN " + EMPLOYEES_OF,
                    companyId, companyId);
        }
        total += jdbc.update("DELETE FROM company_events WHERE company_id = ?", companyId);
        // Break the employees' own references (self-FK reporting manager, work site) before the delete.
        jdbc.update("UPDATE employees SET reporting_manager_id = NULL, work_location_id = NULL WHERE company_id = ?",
                companyId);
        total += jdbc.update("DELETE FROM employees WHERE company_id = ?", companyId);
        total += jdbc.update("DELETE FROM leave_types WHERE company_id = ?", companyId);
        total += jdbc.update("DELETE FROM work_locations WHERE company_id = ?", companyId);
        total += jdbc.update("DELETE FROM company_settings WHERE company_id = ?", companyId);
        total += jdbc.update("DELETE FROM companies WHERE id = ?", companyId);
        log.info("Company {} deleted by its owner ({} rows removed)", companyId, total);
    }
}
