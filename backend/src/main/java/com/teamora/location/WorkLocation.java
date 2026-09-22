package com.teamora.location;

import com.teamora.common.TenantEntity;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * A company-scoped physical work site with a GPS pin and a geofence radius.
 * Staff assigned to an active site can only clock in within {@code radiusM}
 * metres of the pin (enforced server-side; see AttendanceService).
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "work_locations")
public class WorkLocation extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false, length = 120)
    private String name;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal latitude;

    @Column(nullable = false, precision = 9, scale = 6)
    private BigDecimal longitude;

    /** Geofence radius in metres. Floor of 50 (bean validation + DB check). */
    @Column(name = "radius_m", nullable = false)
    private int radiusM;

    @Column(nullable = false)
    private boolean active;
}
