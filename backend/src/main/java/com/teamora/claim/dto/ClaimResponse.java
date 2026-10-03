package com.teamora.claim.dto;

import com.teamora.claim.Claim;
import com.teamora.claim.ClaimCategory;
import com.teamora.claim.ClaimStatus;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/** A single claim row for the staff Claims list. */
public record ClaimResponse(
        UUID id,
        ClaimCategory category,
        String title,
        String amountLabel,
        BigDecimal amount,
        String claimDateLabel,
        ClaimStatus status,
        String statusLabel,
        /** True when a receipt photo is stored — fetch it from GET /api/claims/{id}/receipt. */
        boolean hasReceipt,
        /** Approver's optional reason when declined; null otherwise. */
        String decisionNote
) {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    public static ClaimResponse from(Claim c) {
        BigDecimal amount = c.getAmount().setScale(2, RoundingMode.HALF_UP);
        return new ClaimResponse(
                c.getId(),
                c.getCategory(),
                c.getTitle(),
                amount.toPlainString(),
                amount,
                c.getClaimDate().format(DATE),
                c.getStatus(),
                statusLabel(c.getStatus()),
                c.hasReceipt(),
                c.getDecisionNote());
    }

    private static String statusLabel(ClaimStatus status) {
        return switch (status) {
            case PENDING -> "Pending";
            case APPROVED -> "Approved";
            case REJECTED -> "Rejected";
        };
    }
}
