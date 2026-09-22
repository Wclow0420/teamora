package com.teamora.leave;

import com.teamora.config.TeamoraProperties;
import com.teamora.employee.Employee;
import com.teamora.employee.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Seeds demo leave balances and requests so the staff Leave screen and the
 * admin Approvals screen are populated. Idempotent: only runs when
 * {@code teamora.seed=true} and the leave_requests table is empty. Runs after
 * the employee seeder (Order 3) so people can be looked up by email.
 */
@Slf4j
@Component
@Order(3)
@RequiredArgsConstructor
public class LeaveSeeder implements CommandLineRunner {

    private final LeaveBalanceRepository balances;
    private final LeaveRequestRepository requests;
    private final LeaveTypeRepository leaveTypes;
    private final EmployeeRepository employees;
    private final TeamoraProperties props;

    @Override
    @Transactional
    public void run(String... args) {
        if (!props.seed() || requests.count() > 0) {
            return;
        }

        Optional<Employee> amir = employees.findByEmailIgnoreCase("amir@lumi.com");
        Optional<Employee> faizal = employees.findByEmailIgnoreCase("faizal@lumi.com");
        Optional<Employee> nadia = employees.findByEmailIgnoreCase("nadia@lumi.com");
        if (amir.isEmpty()) {
            log.warn("Leave seeder skipped: amir@lumi.com not found");
            return;
        }
        // The HR manager is the decision-maker for already-decided requests.
        Employee approver = employees.findByEmailIgnoreCase("sarah@lumi.com").orElse(amir.get());

        UUID lumiId = amir.get().getCompany().getId();
        LeaveType annual = type(lumiId, "ANNUAL");
        LeaveType medical = type(lumiId, "MEDICAL");
        LeaveType emergency = type(lumiId, "EMERGENCY");

        List<LeaveBalance> seededBalances = new ArrayList<>();
        if (balances.count() == 0) {
            seededBalances.add(balance(amir.get(), annual, 16, 4));
            seededBalances.add(balance(amir.get(), medical, 14, 6));
            seededBalances.add(balance(amir.get(), emergency, 5, 2));
            balances.saveAll(seededBalances);
        }

        List<LeaveRequest> reqs = new ArrayList<>();

        // Amir's own history (Leave screen). All PAID types so payroll stays unchanged.
        reqs.add(decided(amir.get(), annual,
                LocalDate.of(2026, 6, 18), LocalDate.of(2026, 6, 19),
                "Family trip", LeaveStatus.APPROVED, approver));
        reqs.add(decided(amir.get(), medical,
                LocalDate.of(2026, 6, 10), LocalDate.of(2026, 6, 10),
                "Fever", LeaveStatus.APPROVED, approver));
        reqs.add(pending(amir.get(), emergency,
                LocalDate.of(2026, 6, 22), LocalDate.of(2026, 6, 22),
                "Family emergency"));
        reqs.add(decided(amir.get(), annual,
                LocalDate.of(2026, 7, 1), LocalDate.of(2026, 7, 1),
                "Extra day off", LeaveStatus.REJECTED, approver));

        // Pending requests from others (admin Approvals screen).
        faizal.ifPresent(f -> reqs.add(pending(f, type(f.getCompany().getId(), "MEDICAL"),
                LocalDate.of(2026, 6, 16), LocalDate.of(2026, 6, 16),
                "Clinic appointment")));
        nadia.ifPresent(n -> reqs.add(pending(n, type(n.getCompany().getId(), "ANNUAL"),
                LocalDate.of(2026, 6, 23), LocalDate.of(2026, 6, 25),
                "Short holiday")));

        requests.saveAll(reqs);
        log.info("Seeded {} leave balances and {} leave requests", seededBalances.size(), reqs.size());
    }

    private LeaveType type(UUID companyId, String code) {
        return leaveTypes.findByCompanyIdAndCode(companyId, code)
                .orElseThrow(() -> new IllegalStateException("Missing seeded leave type " + code + " for company " + companyId));
    }

    private LeaveBalance balance(Employee e, LeaveType type, int entitled, int used) {
        LeaveBalance b = LeaveBalance.builder()
                .employee(e)
                .leaveType(type)
                .entitled(entitled)
                .used(used)
                .build();
        b.setCompany(e.getCompany());
        return b;
    }

    private LeaveRequest pending(Employee e, LeaveType type, LocalDate start, LocalDate end, String reason) {
        LeaveRequest r = LeaveRequest.builder()
                .employee(e)
                .leaveType(type)
                .startDate(start)
                .endDate(end)
                .days(days(start, end))
                .reason(reason)
                .status(LeaveStatus.PENDING)
                .build();
        r.setCompany(e.getCompany());
        return r;
    }

    private LeaveRequest decided(Employee e, LeaveType type, LocalDate start, LocalDate end,
                                 String reason, LeaveStatus status, Employee decidedBy) {
        LeaveRequest r = LeaveRequest.builder()
                .employee(e)
                .leaveType(type)
                .startDate(start)
                .endDate(end)
                .days(days(start, end))
                .reason(reason)
                .status(status)
                .decidedBy(decidedBy)
                .decidedAt(Instant.now())
                .build();
        r.setCompany(e.getCompany());
        return r;
    }

    private int days(LocalDate start, LocalDate end) {
        return (int) (ChronoUnit.DAYS.between(start, end) + 1);
    }
}
