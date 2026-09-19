package com.teamora.payroll;

import com.teamora.config.TeamoraProperties;
import com.teamora.employee.Employee;
import com.teamora.employee.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Seeds June 2026 payslips. Idempotent: only runs when {@code teamora.seed=true}
 * and the payslips table is empty. Runs after the employee seeder (Order 1).
 *
 * Amir's payslip matches the app's Payslip screen exactly. The other seeded
 * employees get a believable June 2026 payslip so the admin run summary
 * aggregates to a correct (not faked) total across whoever is seeded.
 */
@Slf4j
@Component
@Order(5)
@RequiredArgsConstructor
public class PayslipSeeder implements CommandLineRunner {

    private static final String PERIOD = "2026-06";
    private static final LocalDate PAY_DATE = LocalDate.of(2026, 6, 28);

    private final PayslipRepository payslips;
    private final EmployeeRepository employees;
    private final TeamoraProperties props;

    @Override
    @Transactional
    public void run(String... args) {
        if (!props.seed() || payslips.count() > 0) {
            return;
        }

        List<Payslip> rows = new ArrayList<>();

        // Amir — exact match for the staff Payslip screen.
        find("amir@lumi.com").ifPresent(e -> {
            Payslip p = Payslip.builder()
                    .employee(e)
                    .period(PERIOD)
                    .basic(money(4000.00))
                    .overtime(money(320.00))
                    .claims(money(150.00))
                    .bonus(money(200.00))
                    .gross(money(4670.00))
                    .epf(money(440.00))
                    .socso(money(19.75))
                    .eis(money(7.90))
                    .pcb(money(0.00))
                    .deductions(money(384.50))
                    .net(money(4285.50))
                    .payDate(PAY_DATE)
                    .status(PayslipStatus.IN_REVIEW)
                    .build();
            p.setCompany(e.getCompany());
            rows.add(p);
        });

        // Other Lumi Foods staff — reasonable RM basic pay; everything derived consistently.
        // Only seeds for employees whose lookup succeeds (see seedSimple).
        seedSimple(rows, "nadia@lumi.com", 6200.00, 0.00, 0.00, 500.00);
        seedSimple(rows, "weijie@lumi.com", 7000.00, 480.00, 0.00, 0.00);
        seedSimple(rows, "priya@lumi.com", 5800.00, 0.00, 120.00, 0.00);
        seedSimple(rows, "faizal@lumi.com", 2800.00, 360.00, 0.00, 0.00);
        seedSimple(rows, "meiling@lumi.com", 5200.00, 0.00, 0.00, 0.00);
        seedSimple(rows, "arjun@lumi.com", 4100.00, 280.00, 90.00, 150.00);
        seedSimple(rows, "siti@lumi.com", 4600.00, 0.00, 60.00, 0.00);
        seedSimple(rows, "sarah@lumi.com", 9500.00, 0.00, 0.00, 1000.00);

        if (rows.isEmpty()) {
            log.warn("PayslipSeeder: no seeded employees found — nothing to seed.");
            return;
        }

        payslips.saveAll(rows);
        log.info("Seeded {} payslips for period {}", rows.size(), PERIOD);
    }

    /**
     * Seeds one payslip with statutory deductions derived from basic pay:
     * EPF 11%, SOCSO 0.5%, EIS 0.2% of basic (HALF_UP, demo rates). Gross is the
     * sum of all earnings; deductions = EPF + SOCSO + EIS; net = gross - deductions.
     */
    private void seedSimple(List<Payslip> rows, String email, double basic, double overtime,
                            double claims, double bonus) {
        find(email).ifPresent(e -> {
            BigDecimal b = money(basic);
            BigDecimal ot = money(overtime);
            BigDecimal cl = money(claims);
            BigDecimal bo = money(bonus);
            BigDecimal gross = b.add(ot).add(cl).add(bo);

            BigDecimal epf = pct(b, 11.0);
            BigDecimal socso = pct(b, 0.5);
            BigDecimal eis = pct(b, 0.2);
            BigDecimal deductions = epf.add(socso).add(eis);
            BigDecimal net = gross.subtract(deductions);

            Payslip p = Payslip.builder()
                    .employee(e)
                    .period(PERIOD)
                    .basic(b)
                    .overtime(ot)
                    .claims(cl)
                    .bonus(bo)
                    .gross(gross)
                    .epf(epf)
                    .socso(socso)
                    .eis(eis)
                    .pcb(money(0.00))
                    .deductions(deductions)
                    .net(net)
                    .payDate(PAY_DATE)
                    .status(PayslipStatus.IN_REVIEW)
                    .build();
            p.setCompany(e.getCompany());
            rows.add(p);
        });
    }

    private Optional<Employee> find(String email) {
        return employees.findByEmailIgnoreCase(email);
    }

    private static BigDecimal money(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP);
    }

    private static BigDecimal pct(BigDecimal base, double percent) {
        return base.multiply(BigDecimal.valueOf(percent))
                .divide(BigDecimal.valueOf(100), 2, RoundingMode.HALF_UP);
    }
}
