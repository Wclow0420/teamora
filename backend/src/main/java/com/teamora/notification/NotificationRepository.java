package com.teamora.notification;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {

    List<Notification> findByEmployeeIdOrderByOccurredAtDesc(UUID employeeId);

    @Modifying
    @Query("update Notification n set n.read = true where n.employee.id = :empId and n.read = false")
    int markAllRead(@Param("empId") UUID empId);

    int countByEmployeeIdAndReadFalse(UUID employeeId);
}
