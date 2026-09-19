package com.teamora.dashboard;

import com.teamora.dashboard.dto.DashboardResponse;
import com.teamora.security.CurrentEmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequiredArgsConstructor
public class DashboardController {

    private final DashboardService dashboardService;
    private final CurrentEmployeeService currentEmployee;

    /** Admin Dashboard summary — restricted to OWNER/HR_ADMIN/MANAGER by SecurityConfig (/api/admin/**). */
    @GetMapping("/api/admin/dashboard")
    public DashboardResponse dashboard() {
        return dashboardService.summary(currentEmployee.require());
    }
}
