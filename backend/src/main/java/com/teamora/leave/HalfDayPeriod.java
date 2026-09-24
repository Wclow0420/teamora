package com.teamora.leave;

/** Which half of the working day a {@link LeaveDurationUnit#HALF_DAY} request covers. */
public enum HalfDayPeriod {
    /** Morning. */
    AM,
    /** Afternoon. */
    PM;

    /** Human label, e.g. "AM". */
    public String label() {
        return name();
    }
}
