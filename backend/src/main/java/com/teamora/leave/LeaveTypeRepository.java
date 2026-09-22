package com.teamora.leave;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveTypeRepository extends JpaRepository<LeaveType, UUID> {

    List<LeaveType> findByCompanyIdOrderBySortOrderAscNameAsc(UUID companyId);

    List<LeaveType> findByCompanyIdAndActiveTrueOrderBySortOrderAscNameAsc(UUID companyId);

    Optional<LeaveType> findByIdAndCompanyId(UUID id, UUID companyId);

    Optional<LeaveType> findByCompanyIdAndCode(UUID companyId, String code);

    boolean existsByCompanyIdAndCode(UUID companyId, String code);

    long countByCompanyId(UUID companyId);
}
