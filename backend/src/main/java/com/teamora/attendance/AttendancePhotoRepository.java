package com.teamora.attendance;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface AttendancePhotoRepository extends JpaRepository<AttendancePhoto, UUID> {
}
