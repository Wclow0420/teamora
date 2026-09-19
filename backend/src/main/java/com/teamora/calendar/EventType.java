package com.teamora.calendar;

/** Kind of company calendar event. Persisted as VARCHAR(16). */
public enum EventType {
    HOLIDAY,
    EVENT,
    TOWNHALL,
    BIRTHDAY;

    /** Theme accent colour key for this event's dot/icon. */
    public String accentColorKey() {
        return switch (this) {
            case HOLIDAY -> "amber";
            case EVENT -> "violet";
            case TOWNHALL -> "sage";
            case BIRTHDAY -> "violet";
        };
    }

    /** UI icon name for this event type. */
    public String iconName() {
        return switch (this) {
            case HOLIDAY -> "star";
            case EVENT -> "heart";
            case TOWNHALL -> "megaphone";
            case BIRTHDAY -> "heart";
        };
    }
}
