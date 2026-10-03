package com.teamora.calendar;

import com.teamora.calendar.dto.CalendarDtos.CompanyEventResponse;
import com.teamora.calendar.dto.CalendarDtos.CreateCompanyEventRequest;
import com.teamora.calendar.dto.CalendarDtos.HolidayImportRequest;
import com.teamora.calendar.dto.CalendarDtos.HolidayImportResponse;
import com.teamora.calendar.dto.CalendarDtos.HolidaySuggestionsResponse;
import com.teamora.calendar.dto.CalendarDtos.UpdateCompanyEventRequest;
import com.teamora.common.exception.BadRequestException;
import com.teamora.security.CurrentEmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
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

    /**
     * Suggested Malaysian public holidays for {@code year} (defaults to the current year), each
     * flagged when the company already has a HOLIDAY on that date. A suggestion list only —
     * nothing is inserted until the admin confirms via {@code holiday-import}.
     */
    @GetMapping("/api/admin/calendar/holiday-suggestions")
    @PreAuthorize("hasAnyRole('OWNER','HR_ADMIN')")
    public HolidaySuggestionsResponse holidaySuggestions(@RequestParam(required = false) Integer year) {
        int y = year != null ? year : LocalDate.now(com.teamora.common.Zones.KL).getYear();
        return calendarService.holidaySuggestions(currentEmployee.require().getCompany().getId(), y);
    }

    /** Add the admin-picked holidays as HOLIDAY events; dates that already have one are skipped. */
    @PostMapping("/api/admin/calendar/holiday-import")
    @PreAuthorize("hasAnyRole('OWNER','HR_ADMIN')")
    public HolidayImportResponse importHolidays(@Valid @RequestBody HolidayImportRequest req) {
        return calendarService.importHolidays(currentEmployee.require().getCompany(), req);
    }

    private static YearMonth parseMonth(String month) {
        if (month == null || month.isBlank()) {
            return YearMonth.now(com.teamora.common.Zones.KL);
        }
        try {
            return YearMonth.parse(month);
        } catch (DateTimeParseException ex) {
            throw new BadRequestException("month must be in yyyy-MM format");
        }
    }
}
