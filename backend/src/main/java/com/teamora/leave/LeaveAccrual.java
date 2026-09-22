package com.teamora.leave;

/**
 * How a leave type's entitlement is granted. Persisted as VARCHAR(16).
 *
 * <ul>
 *   <li>{@code FIXED_ANNUAL}    — a fixed number of days granted per year.</li>
 *   <li>{@code MONTHLY_ACCRUAL} — days accrue month by month (reserved for v2).</li>
 *   <li>{@code NONE}            — no entitlement tracking (e.g. unpaid leave).</li>
 * </ul>
 */
public enum LeaveAccrual {
    FIXED_ANNUAL,
    MONTHLY_ACCRUAL,
    NONE
}
