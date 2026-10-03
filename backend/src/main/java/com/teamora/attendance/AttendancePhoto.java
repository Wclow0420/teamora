package com.teamora.attendance;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;
import java.util.UUID;

/**
 * The clock-in selfie of one attendance record, kept in its own table (V23) so
 * loading attendance rows never loads photo bytes. Plain {@code byte[]} maps to
 * JDBC VARBINARY → Postgres {@code bytea} (no {@code @Lob}, which would map to a
 * large-object OID and break {@code ddl-auto: validate}).
 */
@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "attendance_photos")
public class AttendancePhoto {

    /** Same id as the attendance record it belongs to. */
    @Id
    @Column(name = "record_id")
    private UUID recordId;

    @Column(name = "company_id", nullable = false)
    private UUID companyId;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "photo", nullable = false, columnDefinition = "bytea")
    private byte[] photo;

    @Column(name = "content_type", nullable = false, length = 32)
    private String contentType;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}
