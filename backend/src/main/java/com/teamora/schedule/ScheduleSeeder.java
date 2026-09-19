package com.teamora.schedule;

import com.teamora.config.TeamoraProperties;
import com.teamora.employee.Employee;
import com.teamora.employee.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** Demo shifts for the current week so the scheduling screen has a roster. */
@Slf4j
@Component
@Order(7)
@RequiredArgsConstructor
public class ScheduleSeeder implements CommandLineRunner {

    private final ShiftRepository shifts;
    private final EmployeeRepository employees;
    private final TeamoraProperties props;

    @Override
    @Transactional
    public void run(String... args) {
        if (!props.seed() || shifts.count() > 0) {
            return;
        }
        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate tuesday = monday.plusDays(1);
        LocalDate wednesday = monday.plusDays(2);

        int n = 0;
        n += seed("amir@lumi.com", tuesday, ShiftType.MORNING);
        n += seed("nadia@lumi.com", tuesday, ShiftType.REMOTE);
        n += seed("weijie@lumi.com", tuesday, ShiftType.REMOTE);
        n += seed("arjun@lumi.com", wednesday, ShiftType.MORNING);
        n += seed("meiling@lumi.com", wednesday, ShiftType.EVENING);
        n += seed("siti@lumi.com", wednesday, ShiftType.MORNING);
        if (n > 0) {
            log.info("Seeded {} shifts for the current week", n);
        }
    }

    private int seed(String email, LocalDate date, ShiftType type) {
        return employees.findByEmailIgnoreCase(email).map(e -> {
            Shift s = Shift.builder().employee(e).workDate(date).shiftType(type).build();
            s.setCompany(e.getCompany());
            shifts.save(s);
            return 1;
        }).orElse(0);
    }
}
