package com.teamora.leave;

import com.teamora.leave.dto.LeaveBalanceResponse;
import com.teamora.leave.dto.OverrideLeaveBalanceRequest;
import com.teamora.security.CurrentEmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

/** Admin view of (and control over) an employee's leave entitlement per leave year. */
@RestController
@RequestMapping("/api/admin/leave/balances")
@RequiredArgsConstructor
public class LeaveBalanceController {

    private final LeaveBalanceService leaveBalances;
    private final CurrentEmployeeService currentEmployee;

    /**
     * One employee's balances for {@code year} (default: the current leave year).
     * Company-scoped; open to the whole admin surface (OWNER / HR_ADMIN / MANAGER)
     * because approvers need to see what someone has left.
     */
    @GetMapping
    public List<LeaveBalanceResponse> forEmployee(@RequestParam UUID employeeId,
                                                  @RequestParam(required = false) Integer year) {
        return leaveBalances.balancesForEmployee(currentEmployee.require(), employeeId, year);
    }

    /** Override an employee's entitlement for one leave type + year. Owners / HR admins only. */
    @PatchMapping
    @PreAuthorize("hasAnyRole('OWNER','HR_ADMIN')")
    public LeaveBalanceResponse override(@Valid @RequestBody OverrideLeaveBalanceRequest req) {
        return leaveBalances.override(currentEmployee.require(), req.employeeId(), req.leaveTypeId(),
                req.leaveYear(), req.entitled());
    }
}
