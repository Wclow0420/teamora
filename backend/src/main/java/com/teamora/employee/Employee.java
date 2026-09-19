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

    /** Tax profile (for PCB/MTD). Null marital status → treated as SINGLE. */
    @Enumerated(EnumType.STRING)
    @Column(name = "marital_status", length = 16)
    private MaritalStatus maritalStatus;

    @Column(name = "spouse_working")
    private Boolean spouseWorking;

    @Column(name = "num_children", nullable = false)
    private int numChildren;

    @Column(nullable = false)
    private boolean active;

    /** The employee who approves this person's leave/claims (null → company owner). */
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporting_manager_id")
    private Employee reportingManager;

    /** First letter of the name, used for the avatar tile. */
    public String getInitial() {
        return (fullName == null || fullName.isBlank()) ? "?" : fullName.substring(0, 1).toUpperCase();
    }
}
