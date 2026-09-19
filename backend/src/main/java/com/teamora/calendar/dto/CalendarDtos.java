package com.teamora.calendar.dto;

import java.util.List;
import java.util.Map;

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
}
