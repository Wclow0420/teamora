package com.teamora.common;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.List;

/**
 * Weekday bitmask helpers for a work schedule. Bit layout (LSB first):
 * {@code bit0=Mon, bit1=Tue, bit2=Wed, bit3=Thu, bit4=Fri, bit5=Sat, bit6=Sun}.
 *
 * <ul>
 *   <li>Mon–Fri  = {@code 0b0011111} = 31</li>
 *   <li>Full week = {@code 0b1111111} = 127</li>
 * </ul>
 *
 * A {@link DayOfWeek} maps to a bit via {@code getValue() - 1} (Mon=1 → bit0).
 */
public final class WorkWeek {

    /** Mon–Fri (the common default). */
    public static final int MON_TO_FRI = 0b0011111; // 31
    /** All seven days. */
    public static final int FULL_WEEK = 0b1111111;  // 127

    private WorkWeek() {
    }

    /** Is {@code day} a scheduled working day under {@code mask}? */
    public static boolean isWorkingDay(int mask, DayOfWeek day) {
        int bit = day.getValue() - 1; // Mon=1 → 0 … Sun=7 → 6
        return (mask & (1 << bit)) != 0;
    }

    /** Count the dates in {@code ym} whose weekday is a scheduled working day. */
    public static int scheduledDays(int mask, YearMonth ym) {
        int count = 0;
        int len = ym.lengthOfMonth();
        for (int d = 1; d <= len; d++) {
            if (isWorkingDay(mask, ym.atDay(d).getDayOfWeek())) {
                count++;
            }
        }
        return count;
    }

    /** The scheduled working dates within {@code ym}, in calendar order. */
    public static List<LocalDate> scheduledDates(int mask, YearMonth ym) {
        List<LocalDate> dates = new ArrayList<>();
        int len = ym.lengthOfMonth();
        for (int d = 1; d <= len; d++) {
            LocalDate date = ym.atDay(d);
            if (isWorkingDay(mask, date.getDayOfWeek())) {
                dates.add(date);
            }
        }
        return dates;
    }
}
