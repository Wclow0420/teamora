package com.teamora.schedule.dto;

import com.teamora.employee.Employee;
import com.teamora.schedule.Shift;
import com.teamora.schedule.ShiftType;
import jakarta.validation.constraints.NotNull;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public final class ScheduleDtos {

    private ScheduleDtos() {}

    /** One employee's shift on a day, with labels/colour pre-resolved. */
    public record ShiftRow(
            UUID employeeId,
            String employeeName,
            String initial,
            String department,
            String shiftType,
            String shiftLabel,
            String timeLabel,
            String shiftColorKey
    ) {
        public static ShiftRow from(Shift s) {
            Employee e = s.getEmployee();
            ShiftType t = s.getShiftType();
            return new ShiftRow(
                    e.getId(),
                    e.getFullName(),
                    e.getInitial(),
                    e.getDepartment(),
                    t.name(),
                    t.label(),
                    t.timeLabel(),
                    t.accentColorKey());
        }
    }

    /** A single day of the week with its on-shift roster. */
    public record DayShifts(
            String date,          // YYYY-MM-DD
            String weekdayLabel,  // Mon
            String dayLabel,      // 16
            int onShift,
            List<ShiftRow> shifts
    ) {}

    /** A Monday→Sunday week of shifts. */
    public record WeekScheduleResponse(
            String weekLabel,     // e.g. "16–22 June"
            List<DayShifts> days
    ) {}

    /** Assign (upsert) a shift for an employee on a date. */
    public record AssignShiftRequest(
            @NotNull UUID employeeId,
            @NotNull LocalDate date,
            @NotNull ShiftType shiftType
    ) {}
}
