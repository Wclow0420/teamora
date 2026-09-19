package com.teamora.attendance;

/** Lifecycle/state of a single day's attendance record. */
public enum AttendanceStatus {
    WORKING,
    PRESENT,
    LATE,
    REMOTE,
    ON_LEAVE,
    ABSENT
}
