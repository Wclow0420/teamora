package com.teamora.attendance;

import com.teamora.common.TenantEntity;
import com.teamora.employee.Employee;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

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

    private String location;

    /** GPS coordinates captured at clock-in (geofence audit). Null when unassigned/no coords. */
    @Column(name = "clock_in_lat", precision = 9, scale = 6)
    private BigDecimal clockInLat;

    @Column(name = "clock_in_lng", precision = 9, scale = 6)
    private BigDecimal clockInLng;

    /**
     * Front-camera selfie captured at clock-in (attendance proof), stored inline.
     * Plain {@code byte[]} maps to JDBC VARBINARY → Postgres {@code bytea} under
     * Hibernate 6 (no {@code @Lob}, which would map to a large-object OID and
     * break {@code ddl-auto: validate}). Nullable — clock-in without a photo is
     * allowed. Marked lazy (effective only with bytecode enhancement); the live
     * board derives {@code hasPhoto} from {@code clockInPhotoType} to avoid
     * depending on the bytes being present.
     */
    @Basic(fetch = FetchType.LAZY)
    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "clock_in_photo", columnDefinition = "bytea")
    private byte[] clockInPhoto;

    @Column(name = "clock_in_photo_type", length = 32)
    private String clockInPhotoType;
}
