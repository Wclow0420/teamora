package com.teamora.employee;

/**
 * How an employee's basic pay is derived from their stored monthly salary.
 * The monthly salary is always the stored input; per-unit rates derive from it.
 *
 * <ul>
 *   <li>{@code MONTHLY} — paid the full monthly salary, minus unpaid-leave days.</li>
 *   <li>{@code DAILY}   — paid the derived daily rate × paid worked days.</li>
 *   <li>{@code HOURLY}  — paid the derived hourly rate × paid hours.</li>
 * </ul>
 *
 * Persisted as VARCHAR(16).
 */
public enum PayBasis {
    MONTHLY,
    DAILY,
    HOURLY
}
