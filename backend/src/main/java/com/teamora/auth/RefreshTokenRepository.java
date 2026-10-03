package com.teamora.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    /** Look a token up by {@link RefreshToken#hash its hash} — raw tokens are never stored. */
    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Revoke every still-active refresh token of an employee (password change /
     * admin reset / token reuse), optionally sparing one token by its hash — pass
     * {@code ""} to spare none. Returns the number of sessions revoked.
     */
    @Modifying
    @Query("""
            update RefreshToken t set t.revoked = true
            where t.employee.id = :employeeId
              and t.revoked = false
              and t.tokenHash <> :keepTokenHash
            """)
    int revokeAllForEmployee(@Param("employeeId") UUID employeeId, @Param("keepTokenHash") String keepTokenHash);
}
