package com.teamora.audit;

import com.teamora.audit.AuditDtos.AuditEventResponse;
import com.teamora.security.CurrentEmployeeService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequiredArgsConstructor
public class AuditController {

    private final AuditService auditService;
    private final CurrentEmployeeService currentEmployee;

    /**
     * The company's audit trail, newest first (default 100, max 500). With
     * {@code employeeId}, only events about that employee (404 if not in the company).
     */
    @GetMapping("/api/admin/audit")
    @PreAuthorize("hasAnyRole('OWNER','HR_ADMIN')")
    public List<AuditEventResponse> list(@RequestParam(required = false) UUID employeeId,
                                         @RequestParam(required = false) Integer limit) {
        return auditService.list(currentEmployee.require().getCompany().getId(), employeeId, limit);
    }
}
