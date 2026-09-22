package com.teamora.payroll.export;

import com.teamora.common.exception.BadRequestException;
import com.teamora.employee.Employee;
import com.teamora.payroll.PayrollCalculator;
import com.teamora.payroll.Payslip;
import com.teamora.payroll.PayslipFormat;
import com.teamora.payroll.PayslipRepository;
import com.teamora.payroll.dto.ExportFileResponse;
import com.teamora.payroll.dto.StatutorySummaryResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

/**
 * Statutory reporting & export for a payroll period. Reads the <b>persisted</b> payslips for the
 * period (company-scoped) joined with each employee's identity — it never re-runs payroll or
 * recomputes the employee-side statutory figures. Employer-side EPF/SOCSO/EIS (not stored on the
 * payslip) are derived from the stored basic + bonus via {@link PayrollCalculator#statutory} — the
 * same pure function used during a run — so they stay consistent with the stored employee side.
 *
 * <p>Produces the on-screen contribution summary and four downloadable files:
 * contributions CSV, bank-payment CSV, full-payroll CSV, and a best-effort CP39 (PCB) text file.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PayrollExportService {

    private final PayslipRepository payslips;

    private static final DateTimeFormatter GENERATED_AT =
            DateTimeFormatter.ofPattern("d MMM yyyy, HH:mm", Locale.ENGLISH);

    /** The four supported export file types. */
    public enum ExportType {
        CONTRIBUTIONS, BANK, PAYROLL, CP39;

        static ExportType parse(String raw) {
            if (raw == null || raw.isBlank()) {
                throw new BadRequestException("An export type is required (contributions|bank|payroll|cp39).");
            }
            try {
                return ExportType.valueOf(raw.trim().toUpperCase(Locale.ENGLISH));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException("Unknown export type '" + raw
                        + "'. Expected one of: contributions, bank, payroll, cp39.");
            }
        }
    }

    // ---------------- On-screen summary ----------------

    /** Statutory contribution summary for a period (per-employee rows + company totals). */
    public StatutorySummaryResponse summary(String period, UUID companyId) {
        String resolved = requirePeriod(period);
        List<Payslip> run = requireRun(resolved, companyId);

        List<StatutorySummaryResponse.Row> rows = new ArrayList<>();
        BigDecimal tEpfEe = BigDecimal.ZERO, tEpfEr = BigDecimal.ZERO;
        BigDecimal tSocEe = BigDecimal.ZERO, tSocEr = BigDecimal.ZERO;
        BigDecimal tEisEe = BigDecimal.ZERO, tEisEr = BigDecimal.ZERO;
        BigDecimal tPcb = BigDecimal.ZERO, tNet = BigDecimal.ZERO;

        for (Payslip p : sortedByName(run)) {
            Employee e = p.getEmployee();
            PayrollCalculator.Statutory employer = employerContributions(p);

            rows.add(new StatutorySummaryResponse.Row(
                    e.getFullName(),
                    e.getStaffId(),
                    e.getNric(),
                    e.getEpfNo(),
                    PayslipFormat.money(p.getEpf()),
                    PayslipFormat.money(employer.epfEmployer()),
                    e.getSocsoNo(),
                    PayslipFormat.money(p.getSocso()),
                    PayslipFormat.money(employer.socsoEmployer()),
                    PayslipFormat.money(p.getEis()),
                    PayslipFormat.money(employer.eisEmployer()),
                    e.getTaxNo(),
                    PayslipFormat.money(p.getPcb()),
                    PayslipFormat.money(p.getNet())));

            tEpfEe = tEpfEe.add(p.getEpf());
            tEpfEr = tEpfEr.add(employer.epfEmployer());
            tSocEe = tSocEe.add(p.getSocso());
            tSocEr = tSocEr.add(employer.socsoEmployer());
            tEisEe = tEisEe.add(p.getEis());
            tEisEr = tEisEr.add(employer.eisEmployer());
            tPcb = tPcb.add(p.getPcb());
            tNet = tNet.add(p.getNet());
        }

        StatutorySummaryResponse.Totals totals = new StatutorySummaryResponse.Totals(
                PayslipFormat.money(tEpfEe), PayslipFormat.money(tEpfEr),
                PayslipFormat.money(tSocEe), PayslipFormat.money(tSocEr),
                PayslipFormat.money(tEisEe), PayslipFormat.money(tEisEr),
                PayslipFormat.money(tPcb), PayslipFormat.money(tNet));

        return new StatutorySummaryResponse(
                resolved,
                PayslipFormat.periodLabel(resolved),
                java.time.LocalDateTime.now().format(GENERATED_AT),
                rows,
                totals);
    }

    // ---------------- File exports ----------------

    /** Build one export file for a period. Throws 400 if the period has no run or the type is unknown. */
    public ExportFileResponse file(String period, String type, UUID companyId) {
        String resolved = requirePeriod(period);
        ExportType exportType = ExportType.parse(type);
        List<Payslip> run = sortedByName(requireRun(resolved, companyId));

        return switch (exportType) {
            case CONTRIBUTIONS -> csvFile("contributions", resolved, contributionsCsv(run));
            case BANK -> csvFile("bank", resolved, bankCsv(run));
            case PAYROLL -> csvFile("payroll", resolved, payrollCsv(run));
            case CP39 -> new ExportFileResponse(
                    "cp39-" + resolved + ".txt", "text/plain", cp39Text(resolved, run));
        };
    }

    private ExportFileResponse csvFile(String prefix, String period, String content) {
        return new ExportFileResponse(prefix + "-" + period + ".csv", "text/csv", content);
    }

    /** Statutory contribution summary CSV — per employee + a trailing TOTAL row. */
    private String contributionsCsv(List<Payslip> run) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Employee", "Staff ID", "NRIC", "EPF No",
                "EPF Employee", "EPF Employer", "SOCSO No", "SOCSO Employee", "SOCSO Employer",
                "EIS Employee", "EIS Employer", "Tax No", "PCB", "Net Pay"));

        BigDecimal tEpfEe = BigDecimal.ZERO, tEpfEr = BigDecimal.ZERO;
        BigDecimal tSocEe = BigDecimal.ZERO, tSocEr = BigDecimal.ZERO;
        BigDecimal tEisEe = BigDecimal.ZERO, tEisEr = BigDecimal.ZERO;
        BigDecimal tPcb = BigDecimal.ZERO, tNet = BigDecimal.ZERO;

        for (Payslip p : run) {
            Employee e = p.getEmployee();
            PayrollCalculator.Statutory er = employerContributions(p);
            rows.add(List.of(
                    nz(e.getFullName()), nz(e.getStaffId()), nz(e.getNric()), nz(e.getEpfNo()),
                    PayslipFormat.money(p.getEpf()), PayslipFormat.money(er.epfEmployer()),
                    nz(e.getSocsoNo()), PayslipFormat.money(p.getSocso()), PayslipFormat.money(er.socsoEmployer()),
                    PayslipFormat.money(p.getEis()), PayslipFormat.money(er.eisEmployer()),
                    nz(e.getTaxNo()), PayslipFormat.money(p.getPcb()), PayslipFormat.money(p.getNet())));
            tEpfEe = tEpfEe.add(p.getEpf());
            tEpfEr = tEpfEr.add(er.epfEmployer());
            tSocEe = tSocEe.add(p.getSocso());
            tSocEr = tSocEr.add(er.socsoEmployer());
            tEisEe = tEisEe.add(p.getEis());
            tEisEr = tEisEr.add(er.eisEmployer());
            tPcb = tPcb.add(p.getPcb());
            tNet = tNet.add(p.getNet());
        }

        rows.add(List.of("TOTAL", "", "", "",
                PayslipFormat.money(tEpfEe), PayslipFormat.money(tEpfEr),
                "", PayslipFormat.money(tSocEe), PayslipFormat.money(tSocEr),
                PayslipFormat.money(tEisEe), PayslipFormat.money(tEisEr),
                "", PayslipFormat.money(tPcb), PayslipFormat.money(tNet)));
        return CsvUtil.document(rows);
    }

    /** Bank payment CSV — for a bulk salary payout upload. */
    private String bankCsv(List<Payslip> run) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Employee", "Staff ID", "Bank Name", "Bank Account No", "Net Pay"));
        BigDecimal tNet = BigDecimal.ZERO;
        for (Payslip p : run) {
            Employee e = p.getEmployee();
            rows.add(List.of(nz(e.getFullName()), nz(e.getStaffId()), nz(e.getBankName()),
                    nz(e.getBankAccountNo()), PayslipFormat.money(p.getNet())));
            tNet = tNet.add(p.getNet());
        }
        rows.add(List.of("TOTAL", "", "", "", PayslipFormat.money(tNet)));
        return CsvUtil.document(rows);
    }

    /** Full payroll CSV — the complete per-employee breakdown. */
    private String payrollCsv(List<Payslip> run) {
        List<List<String>> rows = new ArrayList<>();
        rows.add(List.of("Employee", "Staff ID", "Basic", "Overtime", "Claims", "Bonus", "Gross",
                "EPF", "SOCSO", "EIS", "PCB", "Deductions", "Net", "Unpaid Days", "Unpaid Deduction"));
        for (Payslip p : run) {
            Employee e = p.getEmployee();
            rows.add(List.of(
                    nz(e.getFullName()), nz(e.getStaffId()),
                    PayslipFormat.money(p.getBasic()), PayslipFormat.money(p.getOvertime()),
                    PayslipFormat.money(p.getClaims()), PayslipFormat.money(p.getBonus()),
                    PayslipFormat.money(p.getGross()), PayslipFormat.money(p.getEpf()),
                    PayslipFormat.money(p.getSocso()), PayslipFormat.money(p.getEis()),
                    PayslipFormat.money(p.getPcb()), PayslipFormat.money(p.getDeductions()),
                    PayslipFormat.money(p.getNet()), Integer.toString(p.getUnpaidDays()),
                    PayslipFormat.money(p.getUnpaidDeduction())));
        }
        return CsvUtil.document(rows);
    }

    /** CP39 (PCB) best-effort text file. */
    private String cp39Text(String period, List<Payslip> run) {
        String employerName = run.isEmpty() ? "" : run.get(0).getEmployee().getCompany().getName();
        List<Cp39Generator.Record> records = new ArrayList<>();
        for (Payslip p : run) {
            Employee e = p.getEmployee();
            records.add(new Cp39Generator.Record(e.getTaxNo(), e.getNric(), e.getFullName(), p.getPcb()));
        }
        return Cp39Generator.document(employerName, period, records);
    }

    // ---------------- helpers ----------------

    /** Employer-side statutory (not persisted) derived from the stored basic + bonus. */
    private static PayrollCalculator.Statutory employerContributions(Payslip p) {
        return PayrollCalculator.statutory(p.getBasic(), p.getBonus());
    }

    private static List<Payslip> sortedByName(List<Payslip> run) {
        return run.stream()
                .sorted(Comparator.comparing(p -> p.getEmployee().getFullName(), String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    private List<Payslip> requireRun(String period, UUID companyId) {
        List<Payslip> run = payslips.findByPeriodAndCompanyId(period, companyId);
        if (run.isEmpty()) {
            throw new BadRequestException(
                    "No payroll run for " + PayslipFormat.periodLabel(period)
                            + ". Run payroll for this period before exporting.");
        }
        return run;
    }

    private static String requirePeriod(String period) {
        if (period == null || period.isBlank()) {
            throw new BadRequestException("A period (YYYY-MM) is required.");
        }
        try {
            return YearMonth.parse(period.trim()).toString();
        } catch (RuntimeException e) {
            throw new BadRequestException("Invalid period, expected YYYY-MM");
        }
    }

    private static String nz(String s) {
        return s == null ? "" : s;
    }
}
