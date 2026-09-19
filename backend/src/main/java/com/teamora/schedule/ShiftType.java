package com.teamora.schedule;

/** A weekly roster shift slot. Labels/colours are pre-resolved for the app. */
public enum ShiftType {
    MORNING,
    EVENING,
    REMOTE,
    OFF;

    public String label() {
        return switch (this) {
            case MORNING -> "Morning";
            case EVENING -> "Evening";
            case REMOTE -> "Remote";
            case OFF -> "Off";
        };
    }

    public String timeLabel() {
        return switch (this) {
            case MORNING -> "9:00 – 6:00";
            case EVENING -> "2:00 – 11:00";
            case REMOTE -> "Flexible";
            case OFF -> "—";
        };
    }

    public String accentColorKey() {
        return switch (this) {
            case MORNING -> "coral";
            case EVENING -> "violet";
            case REMOTE -> "sage";
            case OFF -> "neutral";
        };
    }
}
