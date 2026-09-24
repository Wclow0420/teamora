package com.teamora.leave;

import com.teamora.common.TenantEntity;
import com.teamora.employee.Employee;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * An employee's entitlement and consumption for a single {@link LeaveType} in a
 * single <b>leave year</b> (see {@link LeaveYear}). Rows are provisioned lazily by
 * {@code LeaveBalanceService}; {@code accruedToDate} and the available figure are
 * derived at read time, never stored.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "leave_balances")
public class LeaveBalance extends TenantEntity {

    /** Days are fractional since partial-day leave — always kept at 2dp. */
    public static final int DAY_SCALE = 2;

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    /** The leave year this row belongs to, identified by its starting calendar year. */
    @Column(name = "leave_year", nullable = false)
    private int leaveYear;

    /** The FULL-year entitlement, already prorated for a first-year joiner. */
    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal entitled;

    /** Unused days brought in from the previous leave year (already capped). */
    @Column(name = "carried_forward", nullable = false, precision = 6, scale = 2)
    private BigDecimal carriedForward;

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal used;

    /** Entitled days, never null, at 2dp. */
    public BigDecimal entitledOrZero() {
        return scaled(entitled);
    }

    /** Used days, never null, at 2dp. */
    public BigDecimal usedOrZero() {
        return scaled(used);
    }

    /** Carried-forward days, never null, at 2dp. */
    public BigDecimal carriedForwardOrZero() {
        return scaled(carriedForward);
    }

    /**
     * Days accrued so far in this leave year (never stored):
     * {@code FIXED_ANNUAL} → the full entitlement; {@code MONTHLY_ACCRUAL} →
     * {@code entitled x elapsedMonths / 12}; {@code NONE} → 0. A past leave year
     * counts as fully accrued, a future one as not started.
     */
    public BigDecimal accruedToDate(int startMonth, LocalDate today) {
        LeaveAccrual accrual = leaveType == null ? LeaveAccrual.FIXED_ANNUAL : leaveType.getAccrual();
        BigDecimal entitled = entitledOrZero();
        if (accrual == LeaveAccrual.NONE) {
            return BigDecimal.ZERO.setScale(DAY_SCALE);
        }
        if (accrual == LeaveAccrual.FIXED_ANNUAL) {
            return entitled;
        }
        int elapsedMonths = elapsedMonths(startMonth, today);
        if (elapsedMonths >= 12) {
            return entitled;
        }
        return entitled.multiply(BigDecimal.valueOf(elapsedMonths))
                .divide(BigDecimal.valueOf(12), DAY_SCALE, java.math.RoundingMode.HALF_UP);
    }

    /**
     * Days the employee can actually take: {@code accruedToDate + carriedForward - used}.
     * May be negative when an admin lowers the entitlement after leave was taken — that is
     * surfaced deliberately rather than clamped to zero.
     */
    public BigDecimal available(int startMonth, LocalDate today) {
        return accruedToDate(startMonth, today).add(carriedForwardOrZero()).subtract(usedOrZero());
    }

    /**
     * What was left at the end of this leave year, used as the carry-forward source for
     * the next one. A closed year is fully accrued, so this ignores monthly accrual.
     */
    public BigDecimal availableAtYearEnd() {
        return entitledOrZero().add(carriedForwardOrZero()).subtract(usedOrZero());
    }

    /** Whole months elapsed in this leave year as of {@code today}, clamped to 0..12. */
    private int elapsedMonths(int startMonth, LocalDate today) {
        int currentYear = LeaveYear.yearOf(today, startMonth);
        if (leaveYear < currentYear) {
            return 12;
        }
        if (leaveYear > currentYear) {
            return 0;
        }
        return Math.min(12, Math.max(0, LeaveYear.monthIndexWithinYear(today, startMonth) + 1));
    }

    private static BigDecimal scaled(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(DAY_SCALE, java.math.RoundingMode.HALF_UP);
    }
}
