package com.teamora.location.dto;

import com.teamora.location.WorkLocation;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/** Payloads for company work-location (geofence) management. */
public final class WorkLocationDtos {

    private WorkLocationDtos() {}

    /** Minimum allowed geofence radius, in metres (GPS accuracy floor). */
    public static final int MIN_RADIUS_M = 50;

    /** Default geofence radius, in metres. */
    public static final int DEFAULT_RADIUS_M = 100;

    public record WorkLocationResponse(
            UUID id,
            String name,
            BigDecimal latitude,
            BigDecimal longitude,
            int radiusM,
            boolean active
    ) {
        public static WorkLocationResponse from(WorkLocation w) {
            return new WorkLocationResponse(
                    w.getId(), w.getName(), w.getLatitude(), w.getLongitude(),
                    w.getRadiusM(), w.isActive());
        }
    }

    /** Create a work location. {@code radiusM} defaults to 100 (min 50); {@code active} defaults to true. */
    public record CreateWorkLocationRequest(
            @NotBlank @Size(max = 120) String name,
            @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
            @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
            @Min(MIN_RADIUS_M) Integer radiusM,
            Boolean active
    ) {}

    /** Partial update — any null field is left unchanged. */
    public record UpdateWorkLocationRequest(
            @Size(max = 120) String name,
            @DecimalMin("-90.0") @DecimalMax("90.0") BigDecimal latitude,
            @DecimalMin("-180.0") @DecimalMax("180.0") BigDecimal longitude,
            @Min(MIN_RADIUS_M) Integer radiusM,
            Boolean active
    ) {}
}
