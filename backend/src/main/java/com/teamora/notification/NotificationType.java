package com.teamora.notification;

/** Kinds of in-app notification, each mapped to an accent colour + icon for the staff feed. */
public enum NotificationType {
    LEAVE_APPROVED,
    LEAVE_REJECTED,
    CLAIM_APPROVED,
    CLAIM_REJECTED,
    OVERTIME_APPROVED,
    OVERTIME_REJECTED,
    APPROVAL_REQUEST,   // an approver: a direct report filed a request
    PAYSLIP_READY,
    SHIFT_REMINDER,
    CLOCK_OUT_REMINDER,
    ACCOUNT_DELETION_REQUEST,   // an admin: an employee asked for their account to be deleted
    BANK_DETAILS_CHANGED,       // an employee: someone else changed their salary bank account
    GENERAL;

    /** Theme accent key for the icon tile ("coral" | "amber" | "sage" | "violet" | "neutral"). */
    public String accentColorKey() {
        return switch (this) {
            case LEAVE_APPROVED, CLAIM_APPROVED, OVERTIME_APPROVED -> "sage";
            case LEAVE_REJECTED, CLAIM_REJECTED, OVERTIME_REJECTED, PAYSLIP_READY, ACCOUNT_DELETION_REQUEST,
                 BANK_DETAILS_CHANGED -> "coral";
            case SHIFT_REMINDER -> "violet";
            case CLOCK_OUT_REMINDER, APPROVAL_REQUEST -> "amber";
            case GENERAL -> "neutral";
        };
    }

    /** Icon name in the app's stroke icon set. */
    public String iconName() {
        return switch (this) {
            case LEAVE_APPROVED, CLAIM_APPROVED, OVERTIME_APPROVED -> "check";
            case LEAVE_REJECTED, CLAIM_REJECTED, OVERTIME_REJECTED -> "x";
            case APPROVAL_REQUEST, ACCOUNT_DELETION_REQUEST -> "bell";
            case PAYSLIP_READY, BANK_DETAILS_CHANGED -> "wallet";
            case SHIFT_REMINDER -> "calendar";
            case CLOCK_OUT_REMINDER -> "clock";
            case GENERAL -> "bell";
        };
    }
}
