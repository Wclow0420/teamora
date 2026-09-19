package com.teamora.schedule;

import com.teamora.schedule.dto.ScheduleDtos.AssignShiftRequest;
import com.teamora.schedule.dto.ScheduleDtos.ShiftRow;
import com.teamora.schedule.dto.ScheduleDtos.WeekScheduleResponse;
import com.teamora.security.CurrentEmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

/** Admin scheduling (MANAGER/HR_ADMIN/OWNER via the /api/admin/** rule). */
@RestController
@RequiredArgsConstructor
public class ScheduleController {

    private final ScheduleService scheduleService;
    private final CurrentEmployeeService currentEmployee;

    @GetMapping("/api/admin/schedule")
    public WeekScheduleResponse week(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate weekStart) {
        LocalDate start = weekStart != null
                ? weekStart
                : LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        return scheduleService.week(currentEmployee.require().getCompany().getId(), start);
    }

    @PostMapping("/api/admin/schedule")
    public ShiftRow assign(@Valid @RequestBody AssignShiftRequest req) {
        return scheduleService.assign(currentEmployee.require().getCompany(), req);
    }
}
