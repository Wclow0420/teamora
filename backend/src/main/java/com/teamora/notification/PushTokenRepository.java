package com.teamora.notification;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PushTokenRepository extends JpaRepository<PushToken, UUID> {
    Optional<PushToken> findByToken(String token);
    List<PushToken> findByEmployeeId(UUID employeeId);
    /** Delete a token only if it belongs to this employee (a caller can't unregister someone else's device). */
    long deleteByTokenAndEmployeeId(String token, UUID employeeId);
    void deleteByEmployeeId(UUID employeeId);
}
