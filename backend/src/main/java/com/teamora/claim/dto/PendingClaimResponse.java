package com.teamora.claim.dto;

import com.teamora.claim.Claim;
import com.teamora.claim.ClaimCategory;
import com.teamora.employee.Employee;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import java.util.UUID;

/** A pending claim awaiting an admin decision (approvals queue). */
public record PendingClaimResponse(
        UUID id,
        String employeeName,
        String initial,
        String title,
        ClaimCategory category,
        String amountLabel,
        String claimDateLabel,
        String accentColorKey
) {
    private static final DateTimeFormatter DATE = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH);

    public static PendingClaimResponse from(Claim c) {
        Employee e = c.getEmployee();
        BigDecimal amount = c.getAmount().setScale(2, RoundingMode.HALF_UP);
        return new PendingClaimResponse(
                c.getId(),
                e.getFullName(),
                e.getInitial(),
                c.getTitle(),
                c.getCategory(),
                amount.toPlainString(),
                c.getClaimDate().format(DATE),
                accentColorKey(c.getCategory()));
    }

    /** Maps a category to a "Warm & Human" accent key for the admin tile. */
    private static String accentColorKey(ClaimCategory category) {
        return switch (category) {
            case TRAVEL -> "violet";
            case PETROL -> "amber";
            case MEAL -> "coral";
            case MEDICAL -> "sage";
            case OTHER -> "amber";
        };
    }
}
