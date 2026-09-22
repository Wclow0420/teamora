package com.teamora.payroll;

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
 * A monthly payslip for one employee. One row per (employee, period); {@code period}
 * is {@code YYYY-MM}. All money columns are {@code NUMERIC(12,2)} → {@link BigDecimal}.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(
        name = "payslips",
        uniqueConstraints = @UniqueConstraint(name = "uq_payslip_emp_period", columnNames = {"employee_id", "period"})
)
public class Payslip extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    /** Pay period in {@code YYYY-MM} form, e.g. "2026-06". */
    @Column(nullable = false, length = 7)
    private String period;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal basic;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal overtime;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal claims;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal bonus;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal gross;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal epf;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal socso;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal eis;

    /** PCB/MTD — estimated monthly income tax. */
    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal pcb;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal deductions;

    @Column(nullable = false, precision = 12, scale = 2)
    private BigDecimal net;

    @Column(name = "pay_date")
    private LocalDate payDate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private PayslipStatus status;

    // ---- Compensation transparency (unpaid-leave deduction, derived rate) ----

    /** Unpaid-leave days deducted from the basic this period. */
    @Builder.Default
    @Column(name = "unpaid_days", nullable = false)
    private int unpaidDays = 0;

    /** Amount deducted for unpaid-leave days (informational; already reflected in {@code basic}). */
    @Builder.Default
    @Column(name = "unpaid_deduction", nullable = false, precision = 12, scale = 2)
    private BigDecimal unpaidDeduction = BigDecimal.ZERO;

    /** Paid days that made up the basic (schedule-driven; null for hourly). */
    @Column(name = "paid_days")
    private Integer paidDays;

    /** Derived daily rate for the period (indicative). */
    @Column(name = "daily_rate", precision = 12, scale = 2)
    private BigDecimal dailyRate;
}
