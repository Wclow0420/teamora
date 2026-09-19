package com.teamora.overtime;

public enum OvertimeStatus {
    PENDING,
    APPROVED,
    REJECTED;

    public String label() {
        return switch (this) {
            case PENDING -> "Pending";
            case APPROVED -> "Approved";
            case REJECTED -> "Rejected";
        };
    }
}
