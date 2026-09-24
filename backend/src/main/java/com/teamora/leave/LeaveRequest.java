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
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.UUID;

/** A request to take leave, pending a manager's decision. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "leave_requests")
public class LeaveRequest extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    /**
     * Working-day fraction this request consumes — 1.00 per scheduled working day
     * for FULL_DAY, 0.50 for HALF_DAY, {@code hours / hoursPerDay} for HOURS.
     * This is what the balance is deducted by and what payroll prices.
     */
    @Column(nullable = false, precision = 5, scale = 2)
    private BigDecimal days;

    /** FULL_DAY / HALF_DAY / HOURS. Never null (legacy rows default to FULL_DAY). */
    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "duration_unit", length = 16, nullable = false)
    private LeaveDurationUnit durationUnit = LeaveDurationUnit.FULL_DAY;

    /** AM / PM — set only when {@link #durationUnit} is HALF_DAY. */
    @Enumerated(EnumType.STRING)
    @Column(name = "half_day_period", length = 2)
    private HalfDayPeriod halfDayPeriod;

    /** Optional start time for an HOURS request (informational). */
    @Column(name = "start_time")
    private LocalTime startTime;

    /** Hours requested — set only when {@link #durationUnit} is HOURS. */
    @Column(precision = 4, scale = 2)
    private BigDecimal hours;

    @Column(length = 500)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(length = 16, nullable = false)
    private LeaveStatus status;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "decided_by")
    private Employee decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;
}
