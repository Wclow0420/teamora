package com.teamora.claim;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.util.UUID;

/**
 * The receipt photo bytes of a claim — a second, narrow mapping over the
 * {@code claims} table. Kept off {@link Claim} on purpose: lazy basic fields
 * need bytecode enhancement (not enabled), so mapping the bytes on Claim would
 * pull every receipt into memory whenever a claims list is loaded. Rows are
 * only ever created through {@link Claim}; this entity is read, and written by
 * the bulk update in {@link ClaimReceiptRepository}.
 *
 * Same column mapping as the clock-in selfie: plain {@code byte[]} → JDBC
 * VARBINARY → Postgres {@code bytea} (no {@code @Lob}, which would map to an
 * OID and break {@code ddl-auto: validate}).
 */
@Getter
@NoArgsConstructor
@Entity
@Table(name = "claims")
public class ClaimReceipt {

    @Id
    private UUID id;

    @JdbcTypeCode(SqlTypes.VARBINARY)
    @Column(name = "receipt_photo", columnDefinition = "bytea")
    private byte[] photo;
}
