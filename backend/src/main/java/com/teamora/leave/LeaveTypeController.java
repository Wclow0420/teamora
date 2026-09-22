package com.teamora.leave;

import com.teamora.leave.dto.LeaveTypeDtos.CreateLeaveTypeRequest;
import com.teamora.leave.dto.LeaveTypeDtos.LeaveTypeResponse;
import com.teamora.leave.dto.LeaveTypeDtos.UpdateLeaveTypeRequest;
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
public class LeaveTypeController {

    private final LeaveTypeService leaveTypeService;
    private final CurrentEmployeeService currentEmployee;

    // ---------- Staff (authenticated): active types for the apply picker ----------

    @GetMapping("/api/leave/types")
    public List<LeaveTypeResponse> activeTypes() {
        return leaveTypeService.list(currentEmployee.require().getCompany().getId(), false);
    }

    // ---------- Admin (restricted to management by SecurityConfig) ----------

    /** Full catalogue, including inactive types. */
    @GetMapping("/api/admin/leave/types")
    public List<LeaveTypeResponse> allTypes() {
        return leaveTypeService.list(currentEmployee.require().getCompany().getId(), true);
    }

    @PostMapping("/api/admin/leave/types")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasAnyRole('OWNER','HR_ADMIN')")
    public LeaveTypeResponse create(@Valid @RequestBody CreateLeaveTypeRequest req) {
        return leaveTypeService.create(currentEmployee.require().getCompany(), req);
    }

    @PatchMapping("/api/admin/leave/types/{id}")
    @PreAuthorize("hasAnyRole('OWNER','HR_ADMIN')")
    public LeaveTypeResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateLeaveTypeRequest req) {
        return leaveTypeService.update(currentEmployee.require().getCompany().getId(), id, req);
    }
}
