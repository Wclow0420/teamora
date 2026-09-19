package com.teamora.claim;

/** Lifecycle state of a claim. Persisted as VARCHAR(16). */
public enum ClaimStatus {
    PENDING, APPROVED, REJECTED
}
