package com.teamora.calendar;

import com.teamora.calendar.dto.CalendarDtos.CompanyEventResponse;
import com.teamora.calendar.dto.CalendarDtos.CreateCompanyEventRequest;
import com.teamora.calendar.dto.CalendarDtos.UpdateCompanyEventRequest;
import com.teamora.common.exception.BadRequestException;
import com.teamora.security.CurrentEmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.YearMonth;
import java.time.format.DateTimeParseException;
import java.util.List;
import java.util.UUID;

/**
 * Admin management of the company calendar (public holidays, town halls, events).
 *
 * <p>Reads are open to management by the {@code /api/admin/**} URL rule; mutations are
 * OWNER/HR_ADMIN only (managers approve requests, they don't author company config).
 */
@RestController
@RequiredArgsConstructor
public class CalendarAdminController {

    private final CalendarService calendarService;
    private final CurrentEmployeeService currentEmployee;

    /** All company events in a month. {@code month} is "yyyy-MM" (defaults to the current month). */
    @GetMapping("/api/admin/calendar/events")
    public List<CompanyEventResponse> events(@RequestParam(required = false) String month) {
        return calendarService.listForMonth(
                currentEmployee.require().getCompany().getId(), parseMonth(month));
    }

    @PostMapping("/api/admin/calendar/events")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','HR_ADMIN')")
    public CompanyEventResponse create(@Valid @RequestBody CreateCompanyEventRequest req) {
        return calendarService.create(currentEmployee.require().getCompany(), req);
    }

    @PatchMapping("/api/admin/calendar/events/{id}")
    @PreAuthorize("hasAnyRole('OWNER','HR_ADMIN')")
    public CompanyEventResponse update(@PathVariable UUID id,
                                       @Valid @RequestBody UpdateCompanyEventRequest req) {
        return calendarService.update(currentEmployee.require().getCompany().getId(), id, req);
    }

    @DeleteMapping("/api/admin/calendar/events/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasAnyRole('OWNER','HR_ADMIN')")
    public void delete(@PathVariable UUID id) {
        calendarService.delete(currentEmployee.require().getCompany().getId(), id);
    }

    private static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now();
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("month must be in yyyy-MM format");
        }
    }
}
