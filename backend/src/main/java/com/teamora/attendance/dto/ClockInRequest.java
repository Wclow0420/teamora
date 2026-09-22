package com.teamora.attendance.dto;

import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;

import java.math.BigDecimal;

/**
 * Optional clock-in body. Coordinates are required in effect only when the
 * employee has an active assigned work location (geofence); otherwise ignored.
 *
 * {@code photoBase64} is an optional front-camera selfie (attendance proof). It
 * may be a bare base64 string or a data URL ({@code data:image/jpeg;base64,...})
 * — the service strips a {@code data:...;base64,} prefix if present. Absent →
 * clock-in proceeds without a photo (non-blocking).
 */
public record ClockInRequest(
        @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
        @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
        String photoBase64
) {}
