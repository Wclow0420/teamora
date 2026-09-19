package com.teamora.overtime;

import com.teamora.common.exception.BadRequestException;
import com.teamora.common.exception.ResourceNotFoundException;
import com.teamora.employee.Employee;
import com.teamora.employee.Role;
import com.teamora.notification.ApprovalNotifier;
import com.teamora.notification.NotificationType;
import com.teamora.overtime.dto.OvertimeDtos.OvertimeResponse;
import com.teamora.overtime.dto.OvertimeDtos.PendingOvertimeResponse;
import com.teamora.overtime.dto.OvertimeDtos.SubmitOvertimeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class OvertimeService {

    private final OvertimeRepository overtime;
    private final ApprovalNotifier notifier;

    private static String hoursLabel(java.math.BigDecimal h) {
        return h.stripTrailingZeros().toPlainString() + "h";
    }

    /** The employee's own overtime requests, newest first. */
    public List<OvertimeResponse> mine(Employee employee) {
        return overtime.findByEmployeeIdOrderByWorkDateDesc(employee.getId()).stream()
                .map(OvertimeResponse::from).toList();
    }

    /** Log overtime (PENDING). */
    @Transactional
    public OvertimeResponse submit(Employee employee, SubmitOvertimeRequest req) {
        OvertimeRequest o = OvertimeRequest.builder()
                .employee(employee)
                .workDate(req.workDate())
                .hours(req.hours())
                .reason(req.reason())
                .status(OvertimeStatus.PENDING)
                .build();
        o.setCompany(employee.getCompany());
        OvertimeRequest saved = overtime.save(o);
        notifier.notifyApprover(employee.getId(), employee.getCompany().getId(), NotificationType.APPROVAL_REQUEST,
                "New overtime", employee.getFullName() + " logged " + hoursLabel(saved.getHours()) + " overtime");
        return OvertimeResponse.from(saved);
    }

    /** Approvals queue routed to the caller (manager → reports; HR/Owner → all). */
    public List<PendingOvertimeResponse> pending(Employee caller) {
        boolean override = caller.getRole() == Role.HR_ADMIN || caller.getRole() == Role.OWNER;
        List<OvertimeRequest> rows = override
                ? overtime.findByStatusAndCompanyId(OvertimeStatus.PENDING, caller.getCompany().getId())
                : overtime.findPendingForReportingManager(OvertimeStatus.PENDING, caller.getCompany().getId(), caller.getId());
        return rows.stream().map(PendingOvertimeResponse::from).toList();
    }

    @Transactional
    public void approve(UUID id, Employee caller) {
        decide(id, caller, OvertimeStatus.APPROVED);
    }

    @Transactional
    public void reject(UUID id, Employee caller) {
        decide(id, caller, OvertimeStatus.REJECTED);
    }

    private void decide(UUID id, Employee caller, OvertimeStatus target) {
        OvertimeRequest o = overtime.findByIdWithEmployeeAndManager(id)
                .orElseThrow(() -> ResourceNotFoundException.of("OvertimeRequest", id));
        if (!o.getCompany().getId().equals(caller.getCompany().getId())) {
            throw ResourceNotFoundException.of("OvertimeRequest", id);
        }
        if (o.getStatus() != OvertimeStatus.PENDING) {
            throw new BadRequestException("Only pending overtime can be decided");
        }
        assertCanDecide(o, caller);
        o.setStatus(target);
        o.setDecidedBy(caller);
        o.setDecidedAt(Instant.now());
        boolean approved = target == OvertimeStatus.APPROVED;
        notifier.notifyRequester(o.getEmployee(),
                approved ? NotificationType.OVERTIME_APPROVED : NotificationType.OVERTIME_REJECTED,
                approved ? "Overtime approved" : "Overtime declined",
                "Your " + hoursLabel(o.getHours()) + " overtime was " + (approved ? "approved." : "declined."));
    }

    /** HR_ADMIN/OWNER decide anything; a MANAGER decides only their direct reports'. */
    private void assertCanDecide(OvertimeRequest o, Employee caller) {
        if (caller.getRole() == Role.HR_ADMIN || caller.getRole() == Role.OWNER) {
            return;
        }
        Employee mgr = o.getEmployee().getReportingManager();
        if (mgr == null || !mgr.getId().equals(caller.getId())) {
            throw new AccessDeniedException("You can only decide overtime from your direct reports");
        }
    }
}
