package com.teamora.common;

import com.teamora.common.exception.BadRequestException;

/**
 * Optional body for the reject endpoints (leave / claims / overtime):
 * {@code { "reason": "..." }}. The body itself is optional, so older clients
 * that POST nothing keep working.
 */
public record DeclineRequest(String reason) {

    /** Max length of a decline reason, mirroring the {@code decision_note} columns. */
    public static final int REASON_MAX = 300;

    /**
     * The trimmed reason, or {@code null} when the body / reason is absent or blank.
     *
     * @throws BadRequestException when the trimmed reason exceeds {@link #REASON_MAX} characters
     */
    public static String normalise(DeclineRequest body) {
        if (body == null || body.reason() == null) {
            return null;
        }
        String trimmed = body.reason().trim();
        if (trimmed.isEmpty()) {
            return null;
        }
        if (trimmed.length() > REASON_MAX) {
            throw new BadRequestException("Reason must be " + REASON_MAX + " characters or fewer");
        }
        return trimmed;
    }

    /** Appends a reason to a "… was declined" notification body: ": reason" when present, else ".". */
    public static String declinedSuffix(String note) {
        return note == null ? "." : ": " + note;
    }
}
