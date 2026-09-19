package com.teamora.claim.dto;

import com.teamora.claim.ClaimCategory;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;

/** Payload to submit a new expense claim. */
public record SubmitClaimRequest(
        @NotNull ClaimCategory category,
        @NotBlank String title,
        @NotNull @DecimalMin("0.0") BigDecimal amount,
        @NotNull LocalDate claimDate,
        String receiptUrl
) {
}
