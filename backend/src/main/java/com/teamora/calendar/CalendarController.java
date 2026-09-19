package com.teamora.calendar;

import com.teamora.calendar.dto.CalendarDtos.MonthCalendarResponse;
import com.teamora.security.CurrentEmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@RestController
@RequiredArgsConstructor
public class CalendarController {

    private final CalendarService calendarService;
    private final CurrentEmployeeService currentEmployee;

    /** Month grid + upcoming list for the caller. `month` is "yyyy-MM" (defaults to current). */
    @GetMapping("/api/calendar")
    public MonthCalendarResponse calendar(@RequestParam(required = false) String month) {
        YearMonth ym = month == null ? YearMonth.now() : YearMonth.parse(month);
        return calendarService.month(currentEmployee.require(), ym);
    }
}
