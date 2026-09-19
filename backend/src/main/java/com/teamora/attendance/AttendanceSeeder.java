package com.teamora.attendance;

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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Seeds demo attendance: a week of history for Amir plus today's status for the
 * other demo staff so the admin Live board looks populated. Idempotent — only
 * runs when {@code teamora.seed=true} and the table is empty. Runs after the
 * employee seeder (Order 2) so it can look people up by email.
 */
@Slf4j
@Component
@Order(2)
@RequiredArgsConstructor
public class AttendanceSeeder implements CommandLineRunner {

    private static final ZoneId KL = ZoneId.of("Asia/Kuala_Lumpur");

    private final AttendanceRepository attendance;
    private final EmployeeRepository employees;
    private final TeamoraProperties props;

    @Override
    @Transactional
    public void run(String... args) {
        if (!props.seed() || attendance.count() > 0) {
            return;
        }

        Optional<Employee> amir = employees.findByEmailIgnoreCase("amir@lumi.com");
        if (amir.isEmpty()) {
            log.warn("Attendance seed skipped: amir@lumi.com not found");
            return;
        }

        LocalDate today = LocalDate.now(KL);
        List<AttendanceRecord> records = new ArrayList<>();

        // ----- Amir: today + the past ~5 working days (matching the app demo) -----
        Employee a = amir.get();

        // Today — currently WORKING, clocked in at 9:02, no clock-out yet.
        records.add(building(a, today, AttendanceStatus.WORKING,
                at(today, 9, 2), null, null));

        // Walk backwards through previous days, assigning the demo pattern to the
        // working days (skip weekends) in order: Fri, Thu, Wed, Tue, Mon.
        LocalDate cursor = today.minusDays(1);
        int filled = 0;
        AttendanceStatus[] statuses = {
                AttendanceStatus.PRESENT,   // most recent prior working day — on time
                AttendanceStatus.LATE,
                AttendanceStatus.ON_LEAVE,
                AttendanceStatus.REMOTE,
                AttendanceStatus.PRESENT
        };
        while (filled < statuses.length) {
            if (isWeekend(cursor)) {
                cursor = cursor.minusDays(1);
                continue;
            }
            records.add(amirDay(a, cursor, statuses[filled]));
            filled++;
            cursor = cursor.minusDays(1);
        }

        // ----- Today's status for the rest of the demo staff (admin Live board) -----
        seedToday(records, "nadia@lumi.com", AttendanceStatus.PRESENT, at(today, 8, 55));
        seedToday(records, "weijie@lumi.com", AttendanceStatus.REMOTE, at(today, 9, 1));
        seedToday(records, "priya@lumi.com", AttendanceStatus.ON_LEAVE, null);
        seedToday(records, "faizal@lumi.com", AttendanceStatus.LATE, at(today, 9, 24));
        seedToday(records, "meiling@lumi.com", AttendanceStatus.PRESENT, at(today, 8, 47));
        seedToday(records, "arjun@lumi.com", AttendanceStatus.PRESENT, at(today, 9, 3));
        seedToday(records, "siti@lumi.com", AttendanceStatus.REMOTE, at(today, 9, 10));

        attendance.saveAll(records);
        log.info("Seeded {} attendance records", records.size());
    }

    /** Builds Amir's prior-day record using the demo clock times for each status. */
    private AttendanceRecord amirDay(Employee a, LocalDate date, AttendanceStatus status) {
        return switch (status) {
            // On time: 9:00 → 6:12 PM.
            case PRESENT -> building(a, date, status, at(date, 9, 0), at(date, 18, 12),
                    minutes(date, 9, 0, 18, 12));
            // Late: 9:18 → 6:05 PM.
            case LATE -> building(a, date, status, at(date, 9, 18), at(date, 18, 5),
                    minutes(date, 9, 18, 18, 5));
            // On leave: no clock in/out.
            case ON_LEAVE -> building(a, date, status, null, null, null);
            // Remote / overtime: 8:58 → 6:30 PM.
            case REMOTE -> building(a, date, status, at(date, 8, 58), at(date, 18, 30),
                    minutes(date, 8, 58, 18, 30));
            default -> building(a, date, status, null, null, null);
        };
    }

    private void seedToday(List<AttendanceRecord> out, String email, AttendanceStatus status,
                           Instant clockIn) {
        employees.findByEmailIgnoreCase(email).ifPresent(e ->
                out.add(building(e, LocalDate.now(KL), status, clockIn, null, null)));
    }

    private AttendanceRecord building(Employee e, LocalDate date, AttendanceStatus status,
                                      Instant clockIn, Instant clockOut, Integer workedMinutes) {
        String location = status == AttendanceStatus.REMOTE ? "Remote" : "Bangsar South HQ";
        AttendanceRecord record = AttendanceRecord.builder()
                .employee(e)
                .workDate(date)
                .clockInAt(clockIn)
                .clockOutAt(clockOut)
                .status(status)
                .workedMinutes(workedMinutes)
                .location(location)
                .build();
        record.setCompany(e.getCompany());
        return record;
    }

    private boolean isWeekend(LocalDate d) {
        return d.getDayOfWeek() == DayOfWeek.SATURDAY || d.getDayOfWeek() == DayOfWeek.SUNDAY;
    }

    /** Instant from a local date + wall-clock time at Asia/Kuala_Lumpur. */
    private Instant at(LocalDate date, int hour, int minute) {
        return date.atTime(LocalTime.of(hour, minute)).atZone(KL).toInstant();
    }

    private int minutes(LocalDate date, int inH, int inM, int outH, int outM) {
        return (int) java.time.Duration.between(at(date, inH, inM), at(date, outH, outM)).toMinutes();
    }
}
