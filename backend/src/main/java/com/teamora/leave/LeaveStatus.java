package com.teamora.leave;

/** Lifecycle of a leave request. Persisted as VARCHAR(16). */
public enum LeaveStatus {
    PENDING,
    APPROVED,
    REJECTED;

    /** Human-friendly label for the mobile UI. */
    public String label() {
        return switch (this) {
            case PENDING -> "Pending";
            case APPROVED -> "Approved";
            case REJECTED -> "Rejected";
        };
    }
}
