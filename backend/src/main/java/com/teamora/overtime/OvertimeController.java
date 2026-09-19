package com.teamora.overtime;

import com.teamora.overtime.dto.OvertimeDtos.OvertimeResponse;
import com.teamora.overtime.dto.OvertimeDtos.PendingOvertimeResponse;
import com.teamora.overtime.dto.OvertimeDtos.SubmitOvertimeRequest;
import com.teamora.security.CurrentEmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class OvertimeController {

    private final OvertimeService overtimeService;
    private final CurrentEmployeeService currentEmployee;

    // ---------- Staff ----------
    @GetMapping("/api/overtime")
    public List<OvertimeResponse> mine() {
        return overtimeService.mine(currentEmployee.require());
    }

    @PostMapping("/api/overtime")
    public OvertimeResponse submit(@Valid @RequestBody SubmitOvertimeRequest req) {
        return overtimeService.submit(currentEmployee.require(), req);
    }

    // ---------- Approvals (routed; MANAGER permitted by /api/admin/** rule) ----------
    @GetMapping("/api/admin/overtime")
    public List<PendingOvertimeResponse> pending() {
        return overtimeService.pending(currentEmployee.require());
    }

    @PostMapping("/api/admin/overtime/{id}/approve")
    public void approve(@PathVariable UUID id) {
        overtimeService.approve(id, currentEmployee.require());
    }

    @PostMapping("/api/admin/overtime/{id}/reject")
    public void reject(@PathVariable UUID id) {
        overtimeService.reject(id, currentEmployee.require());
    }
}
