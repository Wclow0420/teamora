package com.teamora.leave;

import com.teamora.common.TenantEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/**
 * A company-scoped, configurable leave type (replaces the old hardcoded enum).
 * Each carries a stable {@code code} (ANNUAL / MEDICAL / EMERGENCY / UNPAID / …)
 * plus a human name, a paid/unpaid flag, an entitlement default and a theme colour.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "leave_types",
        uniqueConstraints = @UniqueConstraint(name = "uq_leave_types_company_code", columnNames = {"company_id", "code"})
)
public class LeaveType extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 64)
    private String name;

    /** Stable key, unique within a company, e.g. "ANNUAL". */
    @Column(nullable = false, length = 24)
    private String code;

    @Column(nullable = false)
    private boolean paid;

    @Column(name = "default_entitlement_days", nullable = false)
    private int defaultEntitlementDays;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private LeaveAccrual accrual;

    /** Theme accent key for the request/dot colour (see AccentKey on the client). */
    @Column(name = "color_key", nullable = false, length = 16)
    private String colorKey;

    @Column(nullable = false)
    private boolean active;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** Human-friendly full label for the UI (currently the name). */
    public String label() {
        return name;
    }
}
