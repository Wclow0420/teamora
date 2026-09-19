package com.teamora.calendar;

import com.teamora.calendar.dto.CalendarDtos.MonthCalendarResponse;
import com.teamora.calendar.dto.CalendarDtos.UpcomingEvent;
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
            for (LocalDate d = from; !d.isAfter(to); d = d.plusDays(1)) {
                addDot(dots, d.getDayOfMonth(), LEAVE_ACCENT);
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
