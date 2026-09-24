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
import java.util.UUID;

/** An employee's entitlement and consumption for a single {@link LeaveType}. */
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

    @Column(nullable = false, precision = 6, scale = 2)
    private BigDecimal entitled;

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

    /** Days still available (may be fractional; can go negative if over-taken). */
    public BigDecimal remaining() {
        return entitledOrZero().subtract(usedOrZero());
    }

    private static BigDecimal scaled(BigDecimal v) {
        return (v == null ? BigDecimal.ZERO : v).setScale(DAY_SCALE, java.math.RoundingMode.HALF_UP);
    }
}
