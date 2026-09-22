package com.teamora.location;

import com.teamora.location.dto.WorkLocationDtos.CreateWorkLocationRequest;
import com.teamora.location.dto.WorkLocationDtos.UpdateWorkLocationRequest;
import com.teamora.location.dto.WorkLocationDtos.WorkLocationResponse;
import com.teamora.security.CurrentEmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class WorkLocationController {

    private final WorkLocationService workLocationService;
    private final CurrentEmployeeService currentEmployee;

    // ---------- Staff (authenticated): active sites for the caller's company ----------

    @GetMapping("/api/work-locations")
    public List<WorkLocationResponse> activeSites() {
        return workLocationService.list(currentEmployee.require().getCompany().getId(), false);
    }

    // ---------- Admin (restricted to management by SecurityConfig) ----------

    /** Full catalogue, including inactive sites. */
    @GetMapping("/api/admin/work-locations")
    public List<WorkLocationResponse> allSites() {
        return workLocationService.list(currentEmployee.require().getCompany().getId(), true);
    }

    @PostMapping("/api/admin/work-locations")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','HR_ADMIN')")
    public WorkLocationResponse create(@Valid @RequestBody CreateWorkLocationRequest req) {
        return workLocationService.create(currentEmployee.require().getCompany(), req);
    }

    @PatchMapping("/api/admin/work-locations/{id}")
    @PreAuthorize("hasAnyRole('OWNER','HR_ADMIN')")
    public WorkLocationResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateWorkLocationRequest req) {
        return workLocationService.update(currentEmployee.require().getCompany().getId(), id, req);
    }
}
