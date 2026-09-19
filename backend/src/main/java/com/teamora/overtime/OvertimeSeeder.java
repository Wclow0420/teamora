package com.teamora.overtime;

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

/** Demo overtime so the approvals inbox has something to review. */
@Slf4j
@Component
@Order(6)
@RequiredArgsConstructor
public class OvertimeSeeder implements CommandLineRunner {

    private final OvertimeRepository overtime;
    private final EmployeeRepository employees;
    private final TeamoraProperties props;

    @Override
    @Transactional
    public void run(String... args) {
        if (!props.seed() || overtime.count() > 0) {
            return;
        }
        employees.findByEmailIgnoreCase("amir@lumi.com").ifPresent(amir -> {
            overtime.save(ot(amir, LocalDate.of(2026, 6, 17), "2.5", "Month-end stock count"));
            overtime.save(ot(amir, LocalDate.of(2026, 6, 11), "1.5", "Client deck before pitch"));
            log.info("Seeded 2 overtime requests for amir@lumi.com");
        });
    }

    private OvertimeRequest ot(Employee e, LocalDate date, String hours, String reason) {
        OvertimeRequest o = OvertimeRequest.builder()
                .employee(e)
                .workDate(date)
                .hours(new BigDecimal(hours))
                .reason(reason)
                .status(OvertimeStatus.PENDING)
                .build();
        o.setCompany(e.getCompany());
        return o;
    }
}
