package com.teamora.company;

import com.teamora.common.BaseEntity;
import com.teamora.common.WorkWeek;
import com.teamora.employee.PayBasis;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Per-company payroll & schedule defaults (1:1 with {@link Company}). New staff
 * inherit these unless they carry their own override columns.
 */
@Getter
@Setter
@NoArgsConstructor
@Entity
@Table(name = "company_settings")
public class CompanySettings extends BaseEntity {

    /** Shares the primary key with the owning company (see {@link #company}). */
    @Id
    @Column(name = "company_id")
    private UUID companyId;

    @OneToOne(fetch = FetchType.LAZY)
    @MapsId
    @JoinColumn(name = "company_id")
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_pay_basis", nullable = false, length = 16)
    private PayBasis defaultPayBasis;

    /** Weekday bitmask (see {@link WorkWeek}). */
    @Column(name = "default_working_days", nullable = false)
    private short defaultWorkingDays;

    @Column(name = "default_hours_per_day", nullable = false, precision = 4, scale = 2)
    private BigDecimal defaultHoursPerDay;

    /** A settings row seeded with the standard Malaysian defaults (Mon–Fri, 8h, monthly). */
    public static CompanySettings defaultsFor(Company company) {
        CompanySettings s = new CompanySettings();
        s.setCompany(company);
        s.setDefaultPayBasis(PayBasis.MONTHLY);
        s.setDefaultWorkingDays((short) WorkWeek.MON_TO_FRI);
        s.setDefaultHoursPerDay(new BigDecimal("8.00"));
        return s;
    }
}
