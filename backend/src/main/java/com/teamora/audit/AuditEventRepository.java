package com.teamora.audit;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AuditEventRepository extends JpaRepository<AuditEvent, UUID> {

    List<AuditEvent> findByCompanyIdOrderByCreatedAtDesc(UUID companyId, Pageable page);

    List<AuditEvent> findByCompanyIdAndTargetEmployeeIdOrderByCreatedAtDesc(UUID companyId, UUID targetEmployeeId,
                                                                           Pageable page);
}
