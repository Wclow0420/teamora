package com.teamora.payroll.export;

import com.teamora.payroll.dto.ExportFileResponse;
import com.teamora.payroll.dto.StatutorySummaryResponse;
import com.teamora.security.CurrentEmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.UUID;

/**
 * Statutory reporting & export endpoints (OWNER/HR_ADMIN only — payroll is sensitive; MANAGER is
 * excluded, matching the payroll-run endpoints). All reads are scoped to the caller's company.
 */
@RestController
@RequiredArgsConstructor
public class PayrollExportController {

    private final PayrollExportService exportService;
    private final CurrentEmployeeService currentEmployee;

    /** Statutory contribution summary for a period (per-employee rows + company totals). */
    @GetMapping("/api/admin/payroll/export/summary")
    @PreAuthorize("hasAnyRole('OWNER','HR_ADMIN')")
    public StatutorySummaryResponse summary(@RequestParam String period) {
        return exportService.summary(period, companyId());
    }

    /** Build one export file for a period: type = contributions | bank | payroll | cp39. */
    @GetMapping("/api/admin/payroll/export/file")
    @PreAuthorize("hasAnyRole('OWNER','HR_ADMIN')")
    public ExportFileResponse file(@RequestParam String period, @RequestParam String type) {
        return exportService.file(period, type, companyId());
    }

    private UUID companyId() {
        return currentEmployee.require().getCompany().getId();
    }
}
