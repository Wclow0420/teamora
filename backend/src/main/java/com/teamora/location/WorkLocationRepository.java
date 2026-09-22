package com.teamora.location;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface WorkLocationRepository extends JpaRepository<WorkLocation, UUID> {

    List<WorkLocation> findByCompanyIdOrderByNameAsc(UUID companyId);

    List<WorkLocation> findByCompanyIdAndActiveTrueOrderByNameAsc(UUID companyId);

    Optional<WorkLocation> findByIdAndCompanyId(UUID id, UUID companyId);
}
