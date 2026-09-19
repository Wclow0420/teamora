package com.teamora.schedule;

import com.teamora.common.exception.ResourceNotFoundException;
import com.teamora.company.Company;
import com.teamora.employee.Employee;
import com.teamora.employee.EmployeeRepository;
import com.teamora.schedule.dto.ScheduleDtos.AssignShiftRequest;
import com.teamora.schedule.dto.ScheduleDtos.DayShifts;
import com.teamora.schedule.dto.ScheduleDtos.ShiftRow;
import com.teamora.schedule.dto.ScheduleDtos.WeekScheduleResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ScheduleService {

    private final ShiftRepository shifts;
    private final EmployeeRepository employees;

    /** A Monday→Sunday week of shifts (OFF excluded from each day's list/count). */
    public WeekScheduleResponse week(UUID companyId, LocalDate weekStart) {
        LocalDate weekEnd = weekStart.plusDays(6);
        List<Shift> rows = shifts.findByCompanyIdAndWorkDateBetween(companyId, weekStart, weekEnd);

        List<DayShifts> days = new ArrayList<>(7);
        for (int i = 0; i < 7; i++) {
            LocalDate day = weekStart.plusDays(i);
            List<ShiftRow> dayRows = rows.stream()
                    .filter(s -> s.getWorkDate().equals(day) && s.getShiftType() != ShiftType.OFF)
                    .map(ShiftRow::from)
                    .toList();
            days.add(new DayShifts(
                    day.toString(),
                    day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH),
                    String.valueOf(day.getDayOfMonth()),
                    dayRows.size(),
                    dayRows));
        }
        return new WeekScheduleResponse(weekLabel(weekStart, weekEnd), days);
    }

    /** "16–22 June" — start day, end day, end month. */
    private String weekLabel(LocalDate start, LocalDate end) {
        return start.getDayOfMonth() + "–" + end.getDayOfMonth() + " "
                + end.getMonth().getDisplayName(TextStyle.FULL, Locale.ENGLISH);
    }

    /** Upsert a shift for an employee on a date (employee must be in the company). */
    @Transactional
    public ShiftRow assign(Company company, AssignShiftRequest req) {
        Employee employee = employees.findByIdAndCompanyId(req.employeeId(), company.getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Employee", req.employeeId()));

        Shift shift = shifts.findByEmployeeIdAndWorkDate(req.employeeId(), req.date())
                .orElseGet(() -> Shift.builder()
                        .employee(employee)
                        .workDate(req.date())
                        .build());
        shift.setShiftType(req.shiftType());
        shift.setCompany(company);
        return ShiftRow.from(shifts.save(shift));
    }
}
