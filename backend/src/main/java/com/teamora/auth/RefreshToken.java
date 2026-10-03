package com.teamora.auth;

import com.teamora.common.BaseEntity;
import com.teamora.employee.Employee;
import jakarta.persistence.*;
import lombok.*;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.UUID;

/**
 * A persisted refresh token, so sessions can be rotated and revoked. Only the
 * SHA-256 hash of the token is stored (V24) — a database leak doesn't hand out
 * live sessions. The raw token exists only in the client and in the response
 * that issued it.
 */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "refresh_tokens")
public class RefreshToken extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    /** Lowercase hex SHA-256 of the raw token. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 200)
    private String tokenHash;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(nullable = false)
    private boolean revoked;

    /** Set when this token was exchanged for a new pair (rotation); null otherwise. */
    @Column(name = "rotated_at")
    private Instant rotatedAt;

    public boolean isActive() {
        return !revoked && expiresAt.isAfter(Instant.now());
    }

    public boolean isExpired() {
        return !expiresAt.isAfter(Instant.now());
    }

    /** The stored form of a raw refresh token. */
    public static String hash(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }
}
