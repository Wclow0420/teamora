package com.teamora.claim.dto;

import com.teamora.claim.ClaimCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Payload to submit a new expense claim.
 *
 * {@code receiptBase64} is an optional receipt photo: bare base64 or a data URL
 * ({@code data:image/jpeg;base64,...}); max 2 MB decoded. Absent/blank → the
 * claim is submitted without a receipt.
 */
public record SubmitClaimRequest(
        @NotNull ClaimCategory category,
        @NotBlank String title,
        @NotNull @DecimalMin("0.0") BigDecimal amount,
        @NotNull LocalDate claimDate,
        String receiptUrl,
        String receiptBase64
) {
}
