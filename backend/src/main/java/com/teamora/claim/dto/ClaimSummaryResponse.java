package com.teamora.claim.dto;

import java.util.List;

/** The staff Claims screen: pending/reimbursed headline figures plus the full list. */
public record ClaimSummaryResponse(
        String pendingTotalLabel,
        String reimbursedThisMonthLabel,
        List<ClaimResponse> claims
) {
}
