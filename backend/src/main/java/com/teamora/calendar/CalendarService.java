package com.teamora.calendar;

import com.teamora.calendar.dto.CalendarDtos.CompanyEventResponse;
import com.teamora.calendar.dto.CalendarDtos.CreateCompanyEventRequest;
import com.teamora.calendar.dto.CalendarDtos.MonthCalendarResponse;
import com.teamora.calendar.dto.CalendarDtos.UpcomingEvent;
import com.teamora.calendar.dto.CalendarDtos.UpdateCompanyEventRequest;
import com.teamora.common.exception.BadRequestException;
import com.teamora.common.exception.ResourceNotFoundException;
import com.teamora.company.Company;
import com.teamora.employee.Employee;
import com.teamora.leave.LeaveRequest;
import com.teamora.leave.LeaveRequestRepository;
import com.teamora.leave.LeaveStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;

/**
 * The company calendar: the staff month grid / upcoming list, plus admin CRUD over
 * {@code company_events}.
 *
 * <p><strong>Payroll depends on this data.</strong> {@link EventType#HOLIDAY} rows are read by
 * {@code CompensationService} when a payroll run computes holiday pay (and they are excluded from
 * unpaid-leave deductions), so adding, editing, re-dating or deleting a HOLIDAY changes what
 * <em>future</em> payroll runs pay for that period. Re-running payroll is idempotent and never
 * clobbers an APPROVED/PAID payslip, so already-approved periods are unaffected.
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CalendarService {

    private static final DateTimeFormatter MONTH_FMT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH);
    private static final DateTimeFormatter UPCOMING_FMT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.ENGLISH);
    private static final int UPCOMING_LIMIT = 5;
    private static final String LEAVE_ACCENT = "coral";

    private final CompanyEventRepository events;
    private final LeaveRequestRepository leave;

    /** The month grid (event dots per day) + the upcoming list for a caller. */
    public MonthCalendarResponse month(Employee caller, YearMonth ym) {
        UUID companyId = caller.getCompany().getId();
        LocalDate monthStart = ym.atDay(1);
        LocalDate monthEnd = ym.atEndOfMonth();

        Map<Integer, List<String>> dots = new LinkedHashMap<>();

        // (a) Company events within the month → coloured dot on their day.
        List<CompanyEvent> inMonth = events.findByCompanyIdAndEventDateBetweenOrderByEventDateAsc(
                companyId, monthStart, monthEnd);
        for (CompanyEvent e : inMonth) {
            addDot(dots, e.getEventDate().getDayOfMonth(), e.getEventType().accentColorKey());
        }

        // (b) Caller's own APPROVED leave overlapping the month → a coral dot per covered in-month day.
        for (LeaveRequest r : leave.findByEmployeeIdOrderByCreatedAtDesc(caller.getId())) {
            if (r.getStatus() != LeaveStatus.APPROVED) {
                continue;
            }
            LocalDate from = r.getStartDate().isAfter(monthStart) ? r.getStartDate() : monthStart;
            LocalDate to = r.getEndDate().isBefore(monthEnd) ? r.getEndDate() : monthEnd;
            String accent = r.getLeaveType() != null ? r.getLeaveType().getColorKey() : LEAVE_ACCENT;
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                addDot(dots, d.getDayOfMonth(), accent);
            }
        }

        // Upcoming: company events from today onwards, soonest first, capped.
        LocalDate today = LocalDate.now();
        List<UpcomingEvent> upcoming = events
                .findByCompanyIdAndEventDateGreaterThanEqualOrderByEventDateAsc(companyId, today).stream()
                .limit(UPCOMING_LIMIT)
                .map(CalendarService::toUpcoming)
                .toList();

        return new MonthCalendarResponse(MONTH_FMT.format(ym), dots, upcoming);
    }

    // ---------- Admin CRUD ----------

    /** Every company event in {@code ym}, earliest first. */
    public List<CompanyEventResponse> listForMonth(UUID companyId, YearMonth ym) {
        return events.findByCompanyIdAndEventDateBetweenOrderByEventDateAsc(
                        companyId, ym.atDay(1), ym.atEndOfMonth()).stream()
                .map(CompanyEventResponse::from)
                .toList();
    }

    @Transactional
    public CompanyEventResponse create(Company company, CreateCompanyEventRequest req) {
        CompanyEvent e = CompanyEvent.builder()
                .title(req.title().trim())
                .eventDate(req.eventDate())
                .eventType(requireAuthorable(req.eventType()))
                .timeLabel(normaliseTimeLabel(req.timeLabel()))
                .build();
        e.setCompany(company);
        return CompanyEventResponse.from(events.save(e));
    }

    @Transactional
    public CompanyEventResponse update(UUID companyId, UUID id, UpdateCompanyEventRequest req) {
        CompanyEvent e = requireInCompany(companyId, id);
        if (req.title() != null) {
            if (req.title().isBlank()) {
                throw new BadRequestException("Title cannot be blank");
            }
            e.setTitle(req.title().trim());
        }
        if (req.eventDate() != null) {
            e.setEventDate(req.eventDate());
        }
        if (req.eventType() != null) {
            e.setEventType(requireAuthorable(req.eventType()));
        }
        if (req.timeLabel() != null) {
            // An explicit blank clears the label.
            e.setTimeLabel(normaliseTimeLabel(req.timeLabel()));
        }
        return CompanyEventResponse.from(e);
    }

    @Transactional
    public void delete(UUID companyId, UUID id) {
        events.delete(requireInCompany(companyId, id));
    }

    /** Resolve an event by id within the caller's company — a foreign row is a 404, never a peek. */
    private CompanyEvent requireInCompany(UUID companyId, UUID id) {
        CompanyEvent e = events.findById(id)
                .orElseThrow(() -> ResourceNotFoundException.of("CompanyEvent", id));
        if (!e.getCompany().getId().equals(companyId)) {
            throw ResourceNotFoundException.of("CompanyEvent", id);
        }
        return e;
    }

    /** Birthdays are derived from employee records, so they cannot be hand-authored. */
    private static EventType requireAuthorable(EventType type) {
        if (type == EventType.BIRTHDAY) {
            throw new BadRequestException("Birthdays are derived from employee records and cannot be created here");
        }
        return type;
    }

    private static String normaliseTimeLabel(String raw) {
        if (raw == null) {
            return null;
        }
        String trimmed = raw.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private static void addDot(Map<Integer, List<String>> dots, int day, String accentKey) {
        dots.computeIfAbsent(day, k -> new ArrayList<>()).add(accentKey);
    }

    private static UpcomingEvent toUpcoming(CompanyEvent e) {
        String dateLabel = UPCOMING_FMT.format(e.getEventDate());
        if (e.getTimeLabel() != null && !e.getTimeLabel().isBlank()) {
            dateLabel = dateLabel + " · " + e.getTimeLabel();
        }
        return new UpcomingEvent(
                e.getEventType().iconName(),
                e.getTitle(),
                dateLabel,
                e.getEventType().accentColorKey());
    }
}
