package com.teamora.location;

import com.teamora.common.exception.ResourceNotFoundException;
import com.teamora.company.Company;
import com.teamora.location.dto.WorkLocationDtos.CreateWorkLocationRequest;
import com.teamora.location.dto.WorkLocationDtos.UpdateWorkLocationRequest;
import com.teamora.location.dto.WorkLocationDtos.WorkLocationResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.UUID;

import static com.teamora.location.dto.WorkLocationDtos.DEFAULT_RADIUS_M;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class WorkLocationService {

    private final WorkLocationRepository workLocations;

    // ---------- reads ----------

    /** Active sites for staff pickers, or the full catalogue (incl. inactive) for admins. */
    public List<WorkLocationResponse> list(UUID companyId, boolean includeInactive) {
        List<WorkLocation> rows = includeInactive
                ? workLocations.findByCompanyIdOrderByNameAsc(companyId)
                : workLocations.findByCompanyIdAndActiveTrueOrderByNameAsc(companyId);
        return rows.stream().map(WorkLocationResponse::from).toList();
    }

    /** Resolve a site by id within a company, or 404. */
    public WorkLocation requireInCompany(UUID companyId, UUID id) {
        return workLocations.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> ResourceNotFoundException.of("WorkLocation", id));
    }

    // ---------- writes ----------

    @Transactional
    public WorkLocationResponse create(Company company, CreateWorkLocationRequest req) {
        WorkLocation w = WorkLocation.builder()
                .name(req.name().trim())
                .latitude(req.latitude())
                .longitude(req.longitude())
                .radiusM(req.radiusM() != null ? req.radiusM() : DEFAULT_RADIUS_M)
                .active(req.active() == null || req.active())
                .build();
        w.setCompany(company);
        return WorkLocationResponse.from(workLocations.save(w));
    }

    @Transactional
    public WorkLocationResponse update(UUID companyId, UUID id, UpdateWorkLocationRequest req) {
        WorkLocation w = requireInCompany(companyId, id);
        if (req.name() != null && !req.name().isBlank()) w.setName(req.name().trim());
        if (req.latitude() != null) w.setLatitude(req.latitude());
        if (req.longitude() != null) w.setLongitude(req.longitude());
        if (req.radiusM() != null) w.setRadiusM(req.radiusM());
        if (req.active() != null) w.setActive(req.active());
        return WorkLocationResponse.from(w);
    }
}
