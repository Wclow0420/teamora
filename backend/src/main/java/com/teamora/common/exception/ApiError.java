package com.teamora.common.exception;

import java.time.Instant;
import java.util.Map;

/** Uniform error body returned for all handled exceptions. */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors
) {
    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, null);
    }

    public static ApiError validation(String message, String path, Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), 400, "Bad Request", message, path, fieldErrors);
    }
}
