package com.teamora.company;

import com.teamora.common.BaseEntity;
import com.teamora.common.HhMmConverter;
import com.teamora.common.WorkWeek;
import com.teamora.employee.PayBasis;
import com.teamora.leave.LeaveYear;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.time.LocalTime;
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

    /**
     * Calendar month (1–12) the company's leave year starts on. 1 = the leave year
     * is the calendar year; 4 = an April–March leave year.
     */
    @Column(name = "leave_year_start_month", nullable = false)
    private int leaveYearStartMonth;

    /** When the working day starts (company-local wall-clock time). */
    @Convert(converter = HhMmConverter.class)
    @Column(name = "work_start_time", nullable = false, length = 5)
    private LocalTime workStartTime = DEFAULT_WORK_START_TIME;

    /** Minutes after {@link #workStartTime} before a clock-in counts as LATE (0–120). */
    @Column(name = "late_grace_minutes", nullable = false)
    private int lateGraceMinutes = DEFAULT_LATE_GRACE_MINUTES;

    public static final LocalTime DEFAULT_WORK_START_TIME = LocalTime.of(9, 0);
    public static final int DEFAULT_LATE_GRACE_MINUTES = 5;

    /** Work start time, never null. */
    public LocalTime workStartTimeOrDefault() {
        return workStartTime == null ? DEFAULT_WORK_START_TIME : workStartTime;
    }

    /** A settings row seeded with the standard Malaysian defaults (Mon–Fri, 8h, monthly). */
    public static CompanySettings defaultsFor(Company company) {
        CompanySettings s = new CompanySettings();
        s.setCompany(company);
        s.setDefaultPayBasis(PayBasis.MONTHLY);
        s.setDefaultWorkingDays((short) WorkWeek.MON_TO_FRI);
        s.setDefaultHoursPerDay(new BigDecimal("8.00"));
        s.setLeaveYearStartMonth(LeaveYear.DEFAULT_START_MONTH);
        s.setWorkStartTime(DEFAULT_WORK_START_TIME);
        s.setLateGraceMinutes(DEFAULT_LATE_GRACE_MINUTES);
        return s;
    }
}
