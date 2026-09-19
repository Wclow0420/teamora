package com.teamora.payroll;

import com.teamora.payroll.dto.PayrollRunRequest;
import com.teamora.payroll.dto.PayrollRunResponse;
import com.teamora.payroll.dto.PayrollSummaryResponse;
import com.teamora.payroll.dto.PayslipResponse;
import com.teamora.security.CurrentEmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class PayrollController {

    private final PayrollService payrollService;
    private final CurrentEmployeeService currentEmployee;

    /** The signed-in employee's payslip history, newest first. */
    @GetMapping("/api/payroll/payslips")
    public List<PayslipResponse> myPayslips() {
        return payrollService.myPayslips(currentEmployee.require());
    }

    /** A single payslip for the signed-in employee, by period (e.g. 2026-06). */
    @GetMapping("/api/payroll/payslips/{period}")
    public PayslipResponse myPayslip(@PathVariable String period) {
        return payrollService.myPayslip(currentEmployee.require(), period);
    }

    /** Admin payroll run summary for a period (defaults to the latest). ADMIN-only via /api/admin/**. */
    @GetMapping("/api/admin/payroll/summary")
    public PayrollSummaryResponse summary(@RequestParam(required = false) String period) {
        return payrollService.summary(period, currentEmployee.require().getCompany().getId());
    }

    // ---- Payroll run (OWNER/HR_ADMIN only — payroll is sensitive; MANAGER is excluded) ----

    /** Current run state for a period (the per-employee breakdown + totals; generated=false if none). */
    @GetMapping("/api/admin/payroll/run")
    @PreAuthorize("hasAnyRole('OWNER','HR_ADMIN')")
    public PayrollRunResponse runView(@RequestParam String period) {
        return payrollService.runView(period, companyId());
    }

    /** Generate/refresh DRAFT payslips for a period from real salary + approved OT + claims. */
    @PostMapping("/api/admin/payroll/run")
    @PreAuthorize("hasAnyRole('OWNER','HR_ADMIN')")
    public PayrollRunResponse runPayroll(@RequestBody PayrollRunRequest body) {
        return payrollService.runPayroll(body.period(), companyId());
    }

    /** Approve a period's run (DRAFT → APPROVED). */
    @PostMapping("/api/admin/payroll/run/{period}/approve")
    @PreAuthorize("hasAnyRole('OWNER','HR_ADMIN')")
    public PayrollRunResponse approveRun(@PathVariable String period) {
        return payrollService.approveRun(period, companyId());
    }

    /** Mark an approved run as paid (APPROVED → PAID). */
    @PostMapping("/api/admin/payroll/run/{period}/mark-paid")
    @PreAuthorize("hasAnyRole('OWNER','HR_ADMIN')")
    public PayrollRunResponse markRunPaid(@PathVariable String period) {
        return payrollService.markRunPaid(period, companyId());
    }

    private UUID companyId() {
        return currentEmployee.require().getCompany().getId();
    }
}
