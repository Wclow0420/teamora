package com.teamora.attendance;

import com.teamora.attendance.dto.AttendanceDayResponse;
import com.teamora.attendance.dto.AttendanceSummaryResponse;
import com.teamora.attendance.dto.LiveAttendanceResponse;
import com.teamora.attendance.dto.LiveStaffRow;
import com.teamora.attendance.dto.TodayStatusResponse;
import com.teamora.common.exception.BadRequestException;
import com.teamora.employee.Employee;
import com.teamora.employee.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.DayOfWeek;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.YearMonth;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceService {

    private static final ZoneId KL = ZoneId.of("Asia/Kuala_Lumpur");
    private static final LocalTime LATE_AFTER = LocalTime.of(9, 5);
    private static final String DEFAULT_LOCATION = "Bangsar South HQ";

    private final AttendanceRepository attendance;
    private final EmployeeRepository employees;

    private LocalDate today() {
        return LocalDate.now(KL);
    }

    // ---------- Clock in / out ----------

    @Transactional
    public TodayStatusResponse clockIn(Employee current) {
        LocalDate day = today();
        AttendanceRecord record = attendance
                .findByEmployeeIdAndWorkDate(current.getId(), day)
                .orElse(null);

        if (record != null && record.getClockInAt() != null) {
            throw new BadRequestException("Already clocked in today");
        }

        Instant now = Instant.now();
        LocalTime localNow = now.atZone(KL).toLocalTime();
        AttendanceStatus status = localNow.isAfter(LATE_AFTER)
                ? AttendanceStatus.LATE
                : AttendanceStatus.PRESENT;

        if (record == null) {
            record = AttendanceRecord.builder()
                    .employee(current)
                    .workDate(day)
                    .build();
            record.setCompany(current.getCompany());
        }
        record.setClockInAt(now);
        record.setStatus(status);
        record.setLocation(DEFAULT_LOCATION);

        return TodayStatusResponse.from(attendance.save(record));
    }

    @Transactional
    public TodayStatusResponse clockOut(Employee current) {
        LocalDate day = today();
        AttendanceRecord record = attendance
                .findByEmployeeIdAndWorkDate(current.getId(), day)
                .orElse(null);

        if (record == null || record.getClockInAt() == null) {
            throw new BadRequestException("Not clocked in today");
        }
        if (record.getClockOutAt() != null) {
            throw new BadRequestException("Already clocked out today");
        }

        Instant now = Instant.now();
        record.setClockOutAt(now);
        record.setWorkedMinutes((int) Duration.between(record.getClockInAt(), now).toMinutes());

        return TodayStatusResponse.from(attendance.save(record));
    }

    // ---------- Reads ----------

    public TodayStatusResponse today(Employee current) {
        return TodayStatusResponse.from(
                attendance.findByEmployeeIdAndWorkDate(current.getId(), today()).orElse(null));
    }

    public AttendanceSummaryResponse myHistory(Employee current, String month) {
        YearMonth ym = parseMonth(month);
        LocalDate from = ym.atDay(1);
        LocalDate to = ym.atEndOfMonth();

        List<AttendanceRecord> records = attendance.findByEmployeeInRange(current.getId(), from, to);

        int present = 0;
        int late = 0;
        int leave = 0;
        long otMinutes = 0;
        for (AttendanceRecord r : records) {
            switch (r.getStatus()) {
                case PRESENT, REMOTE, WORKING -> present++;
                case LATE -> late++;
                case ON_LEAVE -> leave++;
                default -> {
                }
            }
            // Overtime = worked minutes beyond a standard 9h (540m) day.
            if (r.getWorkedMinutes() != null && r.getWorkedMinutes() > 540) {
                otMinutes += r.getWorkedMinutes() - 540;
            }
        }

        double[] weekly = weeklyHours(current.getId());
        double weeklyTotal = 0;
        for (double h : weekly) {
            weeklyTotal += h;
        }

        List<AttendanceDayResponse> days = records.stream()
                .map(AttendanceDayResponse::from)
                .toList();

        return new AttendanceSummaryResponse(
                ym.format(java.time.format.DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)),
                present,
                late,
                leave,
                hoursLabel(otMinutes),
                weekly,
                hoursLabel(Math.round(weeklyTotal * 60)),
                days
        );
    }

    public LiveAttendanceResponse liveBoard(UUID companyId) {
        LocalDate day = today();
        List<AttendanceRecord> records = attendance.findAllByWorkDateWithEmployee(companyId, day);

        Map<UUID, AttendanceRecord> byEmployee = new HashMap<>();
        for (AttendanceRecord r : records) {
            byEmployee.put(r.getEmployee().getId(), r);
        }

        List<Employee> active = employees.findByCompanyIdAndActiveTrue(companyId).stream()
                .sorted((a, b) -> a.getFullName().compareToIgnoreCase(b.getFullName()))
                .toList();

        int inOffice = 0;
        int remote = 0;
        int lateCount = 0;
        int out = 0;
        List<LiveStaffRow> rows = new java.util.ArrayList<>();

        for (Employee e : active) {
            AttendanceRecord r = byEmployee.get(e.getId());
            AttendanceStatus status = r != null ? r.getStatus() : AttendanceStatus.ABSENT;
            switch (status) {
                case PRESENT, WORKING -> inOffice++;
                case REMOTE -> remote++;
                case LATE -> {
                    lateCount++;
                    inOffice++;
                }
                case ON_LEAVE, ABSENT -> out++;
            }
            rows.add(LiveStaffRow.from(e, r));
        }

        return new LiveAttendanceResponse(
                new LiveAttendanceResponse.Counts(inOffice, remote, lateCount, out),
                rows);
    }

    // ---------- Helpers ----------

    private YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now(KL);
        }
        try {
            return YearMonth.parse(month.trim());
        } catch (Exception ex) {
            throw new BadRequestException("Invalid month, expected YYYY-MM");
        }
    }

    /** 7-element Mon..Sun array of worked hours for the current ISO week. */
    private double[] weeklyHours(UUID employeeId) {
        LocalDate today = today();
        LocalDate monday = today.with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);

        double[] hours = new double[7];
        List<AttendanceRecord> records = attendance.findByEmployeeInRange(employeeId, monday, sunday);
        for (AttendanceRecord r : records) {
            int idx = r.getWorkDate().getDayOfWeek().getValue() - 1; // Mon=0 .. Sun=6
            if (r.getWorkedMinutes() != null) {
                hours[idx] += r.getWorkedMinutes() / 60.0;
            }
        }
        return hours;
    }

    /** Minutes → "9h 12m" / "0h". */
    private String hoursLabel(long minutes) {
        if (minutes <= 0) {
            return "0h";
        }
        long h = minutes / 60;
        long m = minutes % 60;
        return m == 0 ? h + "h" : h + "h " + m + "m";
    }
}
