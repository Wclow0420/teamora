package com.teamora.leave;

import com.teamora.leave.dto.ApplyLeaveRequest;
import com.teamora.leave.dto.LeaveBalanceResponse;
import com.teamora.leave.dto.LeaveRequestResponse;
import com.teamora.leave.dto.PendingLeaveResponse;
import com.teamora.security.CurrentEmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class LeaveController {

    private final LeaveService leaveService;
    private final CurrentEmployeeService currentEmployee;

    // ---------- Staff ----------

    @GetMapping("/api/leave/balances")
    public List<LeaveBalanceResponse> balances() {
        return leaveService.myBalances(currentEmployee.require());
    }

    @GetMapping("/api/leave/requests")
    public List<LeaveRequestResponse> requests() {
        return leaveService.myRequests(currentEmployee.require());
    }

    @PostMapping("/api/leave/requests")
    public LeaveRequestResponse apply(@Valid @RequestBody ApplyLeaveRequest req) {
        return leaveService.apply(currentEmployee.require(), req);
    }

    // ---------- Admin (restricted to ADMIN by SecurityConfig) ----------

    @GetMapping("/api/admin/leave/requests")
    public List<PendingLeaveResponse> pending(
            @RequestParam(required = false, defaultValue = "PENDING") LeaveStatus status) {
        // Routed to the caller: HR_ADMIN/OWNER see all pending; a MANAGER sees
        // only their direct reports' requests.
        return leaveService.pending(currentEmployee.require());
    }

    @PostMapping("/api/admin/leave/requests/{id}/approve")
    public void approve(@PathVariable UUID id) {
        leaveService.approve(id, currentEmployee.require());
    }

    @PostMapping("/api/admin/leave/requests/{id}/reject")
    public void reject(@PathVariable UUID id) {
        leaveService.reject(id, currentEmployee.require());
    }
}
