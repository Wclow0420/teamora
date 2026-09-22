package com.teamora.payroll;

import com.teamora.claim.ClaimRepository;
import com.teamora.claim.ClaimStatus;
import com.teamora.common.exception.BadRequestException;
import com.teamora.common.exception.ResourceNotFoundException;
import com.teamora.employee.Employee;
import com.teamora.employee.EmployeeRepository;
import com.teamora.employee.MaritalStatus;
import com.teamora.overtime.OvertimeRepository;
import com.teamora.overtime.OvertimeStatus;
import com.teamora.payroll.dto.PayrollRunResponse;
import com.teamora.payroll.dto.PayrollSummaryResponse;
import com.teamora.payroll.dto.PayslipResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PayrollService {

    /** Malaysian EA overtime: ordinary rate = monthly / 26 days; hourly = /8; OT on a normal day ×1.5. */
    private static final BigDecimal ORDINARY_DAYS = new BigDecimal("26");
    private static final BigDecimal NORMAL_HOURS = new BigDecimal("8");
    private static final BigDecimal OT_MULTIPLIER = new BigDecimal("1.5");

    private final PayslipRepository payslips;
    private final EmployeeRepository employees;
    private final OvertimeRepository overtime;
    private final ClaimRepository claims;
    private final CompensationService compensationService;

    // ---------------- Staff reads ----------------

    /** All of an employee's payslips, newest first. */
    public List<PayslipResponse> myPayslips(Employee me) {
        return payslips.findByEmployeeIdOrderByPeriodDesc(me.getId()).stream()
                .map(PayslipResponse::from)
                .toList();
    }

    /** A single payslip for an employee + period. */
    public PayslipResponse myPayslip(Employee me, String period) {
        Payslip p = payslips.findByEmployeeIdAndPeriod(me.getId(), period)
                .orElseThrow(() -> ResourceNotFoundException.of("Payslip", period));
        return PayslipResponse.from(p);
    }

    /** The employee's most recent payslip, if any. */
    public PayslipResponse latestForMe(Employee me) {
        return payslips.findByEmployeeIdOrderByPeriodDesc(me.getId()).stream()
                .findFirst()
                .map(PayslipResponse::from)
                .orElseThrow(() -> ResourceNotFoundException.of("Payslip", "latest"));
    }

    // ---------------- Admin run summary (used by the Dashboard) ----------------

    /**
     * Aggregate payroll run for a period, scoped to the caller's company. When {@code period}
     * is blank, falls back to that company's latest period. Sums gross, EPF, SOCSO+EIS and net.
     */
    public PayrollSummaryResponse summary(String period, UUID companyId) {
        String resolved = (period == null || period.isBlank())
                ? payslips.findLatestPeriodByCompanyId(companyId)
                : period;
        if (resolved == null) {
            throw ResourceNotFoundException.of("Payroll run", "latest");
        }

        List<Payslip> run = payslips.findByPeriodAndCompanyId(resolved, companyId);
        if (run.isEmpty()) {
            throw ResourceNotFoundException.of("Payroll run", resolved);
        }

        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal epf = BigDecimal.ZERO;
        BigDecimal socsoEis = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        LocalDate payDate = null;

        for (Payslip p : run) {
            gross = gross.add(p.getGross());
            epf = epf.add(p.getEpf());
            socsoEis = socsoEis.add(p.getSocso()).add(p.getEis());
            net = net.add(p.getNet());
            if (payDate == null && p.getPayDate() != null) {
                payDate = p.getPayDate();
            }
        }

        PayslipStatus status = runStatus(run);

        return new PayrollSummaryResponse(
                resolved,
                PayslipFormat.periodLabel(resolved),
                run.size(),
                PayslipFormat.money(gross),
                PayslipFormat.money(epf),
                PayslipFormat.money(socsoEis),
                PayslipFormat.money(net),
                net,
                PayslipFormat.payDateLabel(payDate),
                status,
                PayslipFormat.statusLabel(status)
        );
    }

    /**
     * Net-total label for the admin Dashboard tile: the given period if it has a run,
     * else the company's latest run, else "0.00". Unlike {@link #summary}, this never
     * throws — so it's safe to call inside the dashboard's shared read transaction
     * (a thrown exception would mark that transaction rollback-only).
     */
    public String netLabelForDashboard(String period, UUID companyId) {
        String resolved = period;
        if (resolved == null || resolved.isBlank()
                || payslips.findByPeriodAndCompanyId(resolved, companyId).isEmpty()) {
            resolved = payslips.findLatestPeriodByCompanyId(companyId);
        }
        if (resolved == null) {
            return PayslipFormat.money(BigDecimal.ZERO);
        }
        List<Payslip> run = payslips.findByPeriodAndCompanyId(resolved, companyId);
        BigDecimal net = run.stream().map(Payslip::getNet).reduce(BigDecimal.ZERO, BigDecimal::add);
        return PayslipFormat.money(net);
    }

    // ---------------- Admin payroll run engine ----------------

    /** Current state of a period's run for the admin screen (generated=false if none yet). */
    public PayrollRunResponse runView(String period, UUID companyId) {
        YearMonth ym = parsePeriod(period);
        List<Payslip> run = payslips.findByPeriodAndCompanyId(ym.toString(), companyId);
        return buildRunResponse(ym, companyId, run, skippedCount(companyId));
    }

    /**
     * Generate (or refresh) payslips for {@code period} across the company's active, salaried
     * employees. Earnings = basic (monthly salary) + overtime pay (approved OT hours in the
     * period × the EA hourly OT rate) + approved expense claims in the period. Statutory
     * deductions via {@link PayrollCalculator}. New/refreshed payslips are DRAFT; payslips
     * already APPROVED or PAID are left untouched (idempotent + safe to re-run). Employees
     * without a salary set are skipped and reported in {@code skippedCount}.
     */
    @Transactional
    public PayrollRunResponse runPayroll(String period, UUID companyId) {
        YearMonth ym = parsePeriod(period);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.plusMonths(1).atDay(1);
        LocalDate payDate = ym.atEndOfMonth();

        int skipped = 0;
        for (Employee e : employees.findByCompanyIdAndActiveTrue(companyId)) {
            BigDecimal salary = e.getMonthlySalary();
            if (salary == null || salary.signum() <= 0) {
                skipped++;
                continue;
            }

            Payslip existing = payslips.findByEmployeeIdAndPeriod(e.getId(), ym.toString()).orElse(null);
            if (existing != null
                    && (existing.getStatus() == PayslipStatus.APPROVED || existing.getStatus() == PayslipStatus.PAID)) {
                continue; // locked — don't clobber a finalised payslip
            }

            // Paid basic = monthly salary less unpaid-leave days (schedule-driven), per the
            // employee's pay basis. OT stays EA statutory (on the full monthly salary) — unchanged.
            CompensationService.Compensation comp = compensationService.forPeriod(e, ym);
            BigDecimal paidBasic = comp.paidBasic();

            BigDecimal overtimePay = overtimePay(e.getId(), salary, from, to);
            BigDecimal claimsTotal = claims.sumAmount(e.getId(), ClaimStatus.APPROVED, from, to);
            BigDecimal bonus = BigDecimal.ZERO;

            PayrollCalculator.TaxProfile profile = new PayrollCalculator.TaxProfile(
                    e.getMaritalStatus() == MaritalStatus.MARRIED,
                    Boolean.TRUE.equals(e.getSpouseWorking()),
                    e.getNumChildren());
            PayrollCalculator.Breakdown b = PayrollCalculator.compute(paidBasic, overtimePay, claimsTotal, bonus, profile);

            Payslip p = existing != null ? existing : new Payslip();
            p.setEmployee(e);
            p.setCompany(e.getCompany());
            p.setPeriod(ym.toString());
            p.setBasic(b.basic());
            p.setUnpaidDays(comp.unpaidDays());
            p.setUnpaidDeduction(comp.unpaidDeduction());
            p.setPaidDays(comp.paidDays());
            p.setDailyRate(comp.dailyRate());
            p.setOvertime(b.overtime());
            p.setClaims(b.claims());
            p.setBonus(b.bonus());
            p.setGross(b.gross());
            p.setEpf(b.epf());
            p.setSocso(b.socso());
            p.setEis(b.eis());
            p.setPcb(b.pcb());
            p.setDeductions(b.deductions());
            p.setNet(b.net());
            p.setPayDate(payDate);
            p.setStatus(PayslipStatus.DRAFT);
            payslips.save(p);
        }

        List<Payslip> run = payslips.findByPeriodAndCompanyId(ym.toString(), companyId);
        if (run.isEmpty()) {
            throw new BadRequestException(
                    "No payroll generated — no active employees have a monthly salary set for this period.");
        }
        return buildRunResponse(ym, companyId, run, skipped);
    }

    /** Approve a period's run: DRAFT/IN_REVIEW payslips → APPROVED. */
    @Transactional
    public PayrollRunResponse approveRun(String period, UUID companyId) {
        YearMonth ym = parsePeriod(period);
        List<Payslip> run = requireRun(ym, companyId);
        for (Payslip p : run) {
            if (p.getStatus() == PayslipStatus.DRAFT || p.getStatus() == PayslipStatus.IN_REVIEW) {
                p.setStatus(PayslipStatus.APPROVED);
            }
        }
        payslips.saveAll(run);
        return buildRunResponse(ym, companyId, run, skippedCount(companyId));
    }

    /** Mark an approved run as paid: APPROVED payslips → PAID. */
    @Transactional
    public PayrollRunResponse markRunPaid(String period, UUID companyId) {
        YearMonth ym = parsePeriod(period);
        List<Payslip> run = requireRun(ym, companyId);
        boolean anyApproved = run.stream().anyMatch(p -> p.getStatus() == PayslipStatus.APPROVED);
        if (!anyApproved) {
            throw new BadRequestException("Approve the payroll run before marking it paid.");
        }
        for (Payslip p : run) {
            if (p.getStatus() == PayslipStatus.APPROVED) {
                p.setStatus(PayslipStatus.PAID);
            }
        }
        payslips.saveAll(run);
        return buildRunResponse(ym, companyId, run, skippedCount(companyId));
    }

    // ---------------- helpers ----------------

    private BigDecimal overtimePay(UUID employeeId, BigDecimal salary, LocalDate from, LocalDate to) {
        BigDecimal hours = overtime.sumHoursInPeriod(employeeId, OvertimeStatus.APPROVED, from, to);
        if (hours == null || hours.signum() <= 0) {
            return BigDecimal.ZERO;
        }
        // Hourly OT rate = monthly / 26 / 8 × 1.5 (kept at high precision until the final round).
        BigDecimal hourlyOt = salary
                .divide(ORDINARY_DAYS, 8, RoundingMode.HALF_UP)
                .divide(NORMAL_HOURS, 8, RoundingMode.HALF_UP)
                .multiply(OT_MULTIPLIER);
        return hours.multiply(hourlyOt).setScale(2, RoundingMode.HALF_UP);
    }

    private List<Payslip> requireRun(YearMonth ym, UUID companyId) {
        List<Payslip> run = payslips.findByPeriodAndCompanyId(ym.toString(), companyId);
        if (run.isEmpty()) {
            throw ResourceNotFoundException.of("Payroll run", ym.toString());
        }
        return run;
    }

    private int skippedCount(UUID companyId) {
        return (int) employees.findByCompanyIdAndActiveTrue(companyId).stream()
                .filter(e -> e.getMonthlySalary() == null || e.getMonthlySalary().signum() <= 0)
                .count();
    }

    private PayrollRunResponse buildRunResponse(YearMonth ym, UUID companyId, List<Payslip> run, int skipped) {
        String period = ym.toString();
        if (run.isEmpty()) {
            return new PayrollRunResponse(
                    period, PayslipFormat.periodLabel(period), false, 0, skipped,
                    PayslipFormat.money(BigDecimal.ZERO), PayslipFormat.money(BigDecimal.ZERO),
                    PayslipFormat.money(BigDecimal.ZERO), PayslipFormat.money(BigDecimal.ZERO),
                    BigDecimal.ZERO,
                    PayslipFormat.payDateLabel(ym.atEndOfMonth()),
                    PayslipStatus.DRAFT, PayslipFormat.statusLabel(PayslipStatus.DRAFT),
                    List.of());
        }

        BigDecimal gross = BigDecimal.ZERO;
        BigDecimal statutory = BigDecimal.ZERO;
        BigDecimal pcb = BigDecimal.ZERO;
        BigDecimal net = BigDecimal.ZERO;
        LocalDate payDate = null;
        List<PayrollRunResponse.Line> lines = new ArrayList<>();

        List<Payslip> sorted = run.stream()
                .sorted(Comparator.comparing(p -> p.getEmployee().getFullName(), String.CASE_INSENSITIVE_ORDER))
                .toList();

        for (Payslip p : sorted) {
            gross = gross.add(p.getGross());
            statutory = statutory.add(p.getEpf()).add(p.getSocso()).add(p.getEis());
            pcb = pcb.add(p.getPcb());
            net = net.add(p.getNet());
            if (payDate == null) {
                payDate = p.getPayDate();
            }
            lines.add(new PayrollRunResponse.Line(
                    p.getId(),
                    p.getEmployee().getFullName(),
                    p.getEmployee().getInitial(),
                    p.getEmployee().getDepartment(),
                    PayslipFormat.money(p.getBasic()),
                    PayslipFormat.money(p.getOvertime()),
                    PayslipFormat.money(p.getClaims()),
                    PayslipFormat.money(p.getGross()),
                    PayslipFormat.money(p.getEpf()),
                    PayslipFormat.money(p.getSocso()),
                    PayslipFormat.money(p.getEis()),
                    PayslipFormat.money(p.getPcb()),
                    PayslipFormat.money(p.getDeductions()),
                    PayslipFormat.money(p.getNet()),
                    p.getStatus()));
        }

        PayslipStatus status = runStatus(run);
        return new PayrollRunResponse(
                period, PayslipFormat.periodLabel(period), true, run.size(), skipped,
                PayslipFormat.money(gross), PayslipFormat.money(statutory),
                PayslipFormat.money(pcb), PayslipFormat.money(net), net,
                PayslipFormat.payDateLabel(payDate),
                status, PayslipFormat.statusLabel(status),
                lines);
    }

    /** Run-level status = the least-advanced payslip stage in the run. */
    private static PayslipStatus runStatus(List<Payslip> run) {
        return run.stream()
                .map(Payslip::getStatus)
                .min(Comparator.comparingInt(Enum::ordinal))
                .orElse(PayslipStatus.DRAFT);
    }

    private YearMonth parsePeriod(String period) {
        if (period == null || period.isBlank()) {
            throw new BadRequestException("A period (YYYY-MM) is required.");
        }
        try {
            return YearMonth.parse(period.trim());
        } catch (RuntimeException e) {
            throw new BadRequestException("Invalid period, expected YYYY-MM");
        }
    }
}
