package com.teamora.attendance;

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
import java.util.UUID;

/** One employee's attendance for one calendar day. Unique per (employee, work_date). */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "attendance_records")
public class AttendanceRecord extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "work_date", nullable = false)
    private LocalDate workDate;

    @Column(name = "clock_in_at")
    private Instant clockInAt;

    @Column(name = "clock_out_at")
    private Instant clockOutAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private AttendanceStatus status;

    @Column(name = "worked_minutes")
    private Integer workedMinutes;

    /**
     * Minutes spent clocked OUT in the middle of the day (clock out → clock in
     * again). Worked time is always net of this.
     */
    @Builder.Default
    @Column(name = "break_minutes", nullable = false)
    private int breakMinutes = 0;

    private String location;

    /** GPS coordinates captured at clock-in (geofence audit). Null when unassigned/no coords. */
    @Column(name = "clock_in_lat", precision = 9, scale = 6)
    private BigDecimal clockInLat;

    @Column(name = "clock_in_lng", precision = 9, scale = 6)
    private BigDecimal clockInLng;

    /**
     * Content-type of the clock-in selfie, doubling as the "has a selfie" flag. The
     * bytes live in {@link AttendancePhoto} (attendance_photos, V23) so loading a
     * record never loads the photo.
     */
    @Column(name = "clock_in_photo_type", length = 32)
    private String clockInPhotoType;
}
