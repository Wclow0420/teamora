package com.teamora.calendar.dto;

import com.teamora.calendar.CompanyEvent;
import com.teamora.calendar.EventType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class CalendarDtos {

    private CalendarDtos() {}

    /** A single row in the "Upcoming" list. */
    public record UpcomingEvent(
            String iconName,
            String title,
            String dateLabel,   // e.g. "Thu 18 Jun · 3:00 PM" or "Wed 24 Jun"
            String accentColorKey
    ) {}

    /** The month grid + upcoming list for the staff Calendar screen. */
    public record MonthCalendarResponse(
            String monthLabel,                  // e.g. "June 2026"
            Map<Integer, List<String>> events,  // day-of-month → list of accent colour keys
            List<UpcomingEvent> upcoming
    ) {}

    // ---------- Admin CRUD (company calendar & holiday management) ----------

    /** Max length of the optional time label, mirroring {@code company_events.time_label}. */
    public static final int TIME_LABEL_MAX = 64;

    /** Max length of an event title, mirroring {@code company_events.title}. */
    public static final int TITLE_MAX = 255;

    /** A single company event as managed by admins. */
    public record CompanyEventResponse(
            UUID id,
            String title,
            LocalDate eventDate,
            EventType eventType,
            String accentColorKey,
            String iconName,
            String timeLabel
    ) {
        public static CompanyEventResponse from(CompanyEvent e) {
            return new CompanyEventResponse(
                    e.getId(),
                    e.getTitle(),
                    e.getEventDate(),
                    e.getEventType(),
                    e.getEventType().accentColorKey(),
                    e.getEventType().iconName(),
                    e.getTimeLabel());
        }
    }

    /** Create a company event. {@code BIRTHDAY} is rejected — birthdays are derived, not hand-authored. */
    public record CreateCompanyEventRequest(
            @NotBlank @Size(max = TITLE_MAX) String title,
            @NotNull LocalDate eventDate,
            @NotNull EventType eventType,
            @Size(max = TIME_LABEL_MAX) String timeLabel
    ) {}

    /** Partial update — any null field is left unchanged. */
    public record UpdateCompanyEventRequest(
            @Size(max = TITLE_MAX) String title,
            LocalDate eventDate,
            EventType eventType,
            @Size(max = TIME_LABEL_MAX) String timeLabel
    ) {}
}
