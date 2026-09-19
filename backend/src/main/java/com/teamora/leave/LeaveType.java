package com.teamora.leave;

/** Categories of leave an employee can request. Persisted as VARCHAR(16). */
public enum LeaveType {
    ANNUAL,
    MEDICAL,
    EMERGENCY;

    /** Human-friendly label for the mobile UI, e.g. "Annual Leave". */
    public String label() {
        return switch (this) {
            case ANNUAL -> "Annual Leave";
            case MEDICAL -> "Medical Leave";
            case EMERGENCY -> "Emergency Leave";
        };
    }

    /** Short label used in balance tiles, e.g. "Annual". */
    public String shortLabel() {
        return switch (this) {
            case ANNUAL -> "Annual";
            case MEDICAL -> "Medical";
            case EMERGENCY -> "Emergency";
        };
    }

    /** Theme accent key (see AccentKey on the client) used to colour the request. */
    public String accentColorKey() {
        return switch (this) {
            case ANNUAL -> "coral";
            case MEDICAL -> "sage";
            case EMERGENCY -> "amber";
        };
    }
}
