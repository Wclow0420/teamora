package com.teamora.claim.dto;

import jakarta.validation.constraints.Size;
import jakarta.validation.constraints.DecimalMax;
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
        @NotBlank @Size(max = 120, message = "Title must be at most 120 characters") String title,
        @NotNull @DecimalMin("0.0")
        @DecimalMax(value = "100000.00", message = "Claim amount can't be more than RM 100,000") BigDecimal amount,
        @NotNull LocalDate claimDate,
        @Size(max = 512) String receiptUrl,
        String receiptBase64
) {
}
