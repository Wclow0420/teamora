package com.teamora.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {
    Optional<RefreshToken> findByToken(String token);

    /**
     * Revoke every still-active refresh token of an employee (password change /
     * admin reset), optionally sparing one token — pass {@code ""} to spare none.
     * Returns the number of sessions revoked.
     */
    @Modifying
    @Query("""
            update RefreshToken t set t.revoked = true
            where t.employee.id = :employeeId
              and t.revoked = false
              and t.token <> :keepToken
            """)
    int revokeAllForEmployee(@Param("employeeId") UUID employeeId, @Param("keepToken") String keepToken);
}
