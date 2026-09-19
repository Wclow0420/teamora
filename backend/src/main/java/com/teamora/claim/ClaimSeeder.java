package com.teamora.claim;

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
import java.time.LocalDate;
import java.util.List;

/**
 * Seeds the demo expense claims for amir@lumi.com so the Claims screens match
 * the prototype. Idempotent: only runs when {@code teamora.seed=true} and the
 * claims table is empty.
 */
@Slf4j
@Component
@Order(4)
@RequiredArgsConstructor
public class ClaimSeeder implements CommandLineRunner {

    private final ClaimRepository claims;
    private final EmployeeRepository employees;
    private final TeamoraProperties props;

    @Override
    @Transactional
    public void run(String... args) {
        if (!props.seed() || claims.count() > 0) {
            return;
        }
        Employee amir = employees.findByEmailIgnoreCase("amir@lumi.com").orElse(null);
        if (amir == null) {
            log.warn("ClaimSeeder: amir@lumi.com not found; skipping claim seed");
            return;
        }

        List<Claim> seed = List.of(
                claim(amir, ClaimCategory.TRAVEL, "Travel · Grab to client", "48.00", LocalDate.of(2026, 6, 14), ClaimStatus.APPROVED),
                claim(amir, ClaimCategory.PETROL, "Petrol · KL–Seremban", "120.00", LocalDate.of(2026, 6, 13), ClaimStatus.PENDING),
                claim(amir, ClaimCategory.MEAL, "Client lunch", "86.50", LocalDate.of(2026, 6, 12), ClaimStatus.PENDING),
                claim(amir, ClaimCategory.MEDICAL, "Medical · clinic visit", "60.00", LocalDate.of(2026, 6, 8), ClaimStatus.APPROVED)
        );

        claims.saveAll(seed);
        log.info("Seeded {} demo claims for {}", seed.size(), amir.getEmail());
    }

    private Claim claim(Employee employee, ClaimCategory category, String title,
                        String amount, LocalDate date, ClaimStatus status) {
        Claim claim = Claim.builder()
                .employee(employee)
                .category(category)
                .title(title)
                .amount(new BigDecimal(amount))
                .claimDate(date)
                .status(status)
                .build();
        claim.setCompany(employee.getCompany());
        return claim;
    }
}
