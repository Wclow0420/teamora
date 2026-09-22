package com.teamora.employee;

import com.teamora.common.TenantEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** A person who can sign in — an employee (STAFF) or a manager/HR (ADMIN). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "employees")
public class Employee extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(name = "password_hash", nullable = false)
    private String passwordHash;

    @Column(name = "full_name", nullable = false)
    private String fullName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private Role role;

    @Column(name = "job_title")
    private String jobTitle;

    private String department;

    private String location;

    /** Human-friendly id shown in the app, e.g. "EMP-042". */
    @Column(name = "staff_id", unique = true)
    private String staffId;

    private String phone;

    @Column(name = "join_date")
    private LocalDate joinDate;

    /** Monthly basic salary (RM) — the basis for payroll runs. Null = not set yet. */
    @Column(name = "monthly_salary", precision = 12, scale = 2)
    private BigDecimal monthlySalary;

    // ---- Compensation / schedule overrides (null ⇒ inherit the company default) ----

    /** Pay basis override. Null ⇒ company default. */
    @Enumerated(EnumType.STRING)
    @Column(name = "pay_basis", length = 16)
    private PayBasis payBasis;

    /** Working-days weekday bitmask override (see {@code WorkWeek}). Null ⇒ company default. */
    @Column(name = "working_days")
    private Short workingDays;

    /** Hours-per-day override. Null ⇒ company default. */
    @Column(name = "hours_per_day", precision = 4, scale = 2)
    private BigDecimal hoursPerDay;

    /** Tax profile (for PCB/MTD). Null marital status → treated as SINGLE. */
    @Enumerated(EnumType.STRING)
    @Column(name = "marital_status", length = 16)
    private MaritalStatus maritalStatus;

    @Column(name = "spouse_working")
    private Boolean spouseWorking;

    @Column(name = "num_children", nullable = false)
    private int numChildren;

    // ---- Statutory & bank identity (for reporting/export; all optional) ----

    /** National Registration Identity Card number (MyKad). */
    @Column(name = "nric", length = 20)
    private String nric;

    /** EPF (KWSP) member number. */
    @Column(name = "epf_no", length = 32)
    private String epfNo;

    /** SOCSO (PERKESO) number. */
    @Column(name = "socso_no", length = 32)
    private String socsoNo;

    /** Income tax reference number (LHDN). */
    @Column(name = "tax_no", length = 32)
    private String taxNo;

    /** Bank name for salary payout. */
    @Column(name = "bank_name", length = 64)
    private String bankName;

    /** Bank account number for salary payout. */
    @Column(name = "bank_account_no", length = 40)
    private String bankAccountNo;

    @Column(nullable = false)
    private boolean active;

    /** The employee who approves this person's leave/claims (null → company owner). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporting_manager_id")
    private Employee reportingManager;

    /** Assigned work site for geofenced clock-in (null → no geofence). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "work_location_id")
    private com.teamora.location.WorkLocation workLocation;

    /** First letter of the name, used for the avatar tile. */
    public String getInitial() {
        return (fullName == null || fullName.isBlank()) ? "?" : fullName.substring(0, 1).toUpperCase();
    }
}
