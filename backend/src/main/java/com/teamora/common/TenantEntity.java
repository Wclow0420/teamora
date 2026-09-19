package com.teamora.common;

import com.teamora.company.Company;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.MappedSuperclass;
import lombok.Getter;
import lombok.Setter;

/**
 * Base class for every tenant-scoped entity. Adds the mandatory `company_id`
 * link (the tenant discriminator) on top of the audit timestamps.
 *
 * Services MUST set the company on create, and scope cross-employee/admin
 * queries by company id — see CLAUDE.md / the backend README for the rule.
 */
@Getter
@Setter
@MappedSuperclass
public abstract class TenantEntity extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;
}
