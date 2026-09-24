package com.teamora.leave;

/**
 * How much of a working day a leave request consumes.
 *
 * <p>{@link #FULL_DAY} spans a date range; {@link #HALF_DAY} and {@link #HOURS}
 * apply to a single date only.
 */
public enum LeaveDurationUnit {
    /** One whole day for every scheduled working day in the range. */
    FULL_DAY,
    /** Half of a single working day (morning or afternoon). */
    HALF_DAY,
    /** A number of hours out of a single working day. */
    HOURS
}
