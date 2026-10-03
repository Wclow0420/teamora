package com.teamora.common.exception;

/** A rate limit was hit → HTTP 429 with a {@code Retry-After} header. */
public class TooManyRequestsException extends RuntimeException {

    public static final String MESSAGE = "Too many attempts — try again in a few minutes.";

    private final long retryAfterSeconds;

    public TooManyRequestsException(long retryAfterSeconds) {
        super(MESSAGE);
        this.retryAfterSeconds = Math.max(1, retryAfterSeconds);
    }

    public long getRetryAfterSeconds() {
        return retryAfterSeconds;
    }
}
