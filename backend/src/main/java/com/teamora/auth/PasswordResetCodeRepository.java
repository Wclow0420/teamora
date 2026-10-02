package com.teamora.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface PasswordResetCodeRepository extends JpaRepository<PasswordResetCode, UUID> {

    /** Codes requested since {@code since} — the request rate limit. */
    long countByEmployeeIdAndCreatedAtAfter(UUID employeeId, Instant since);

    /** The employee's current code (newest not yet used/superseded), if any. */
    Optional<PasswordResetCode> findFirstByEmployeeIdAndUsedAtIsNullOrderByCreatedAtDesc(UUID employeeId);

    /** Retire every outstanding code of an employee (a newer code replaces them). */
    @Modifying
    @Query("""
            update PasswordResetCode c set c.usedAt = :now
            where c.employee.id = :employeeId and c.usedAt is null
            """)
    int invalidateOutstanding(@Param("employeeId") UUID employeeId, @Param("now") Instant now);
}
