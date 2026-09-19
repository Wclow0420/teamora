package com.teamora.leave;

import com.teamora.common.exception.BadRequestException;
import com.teamora.common.exception.ResourceNotFoundException;
import com.teamora.employee.Employee;
import com.teamora.employee.Role;
import com.teamora.notification.ApprovalNotifier;
import com.teamora.notification.NotificationType;
import com.teamora.leave.dto.ApplyLeaveRequest;
import com.teamora.leave.dto.LeaveBalanceResponse;
import com.teamora.leave.dto.LeaveRequestResponse;
import com.teamora.leave.dto.PendingLeaveResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaveService {

    private final LeaveBalanceRepository balances;
    private final LeaveRequestRepository requests;
    private final ApprovalNotifier notifier;

    /** The signed-in employee's leave balances (whatever is on record). */
    public List<LeaveBalanceResponse> myBalances(Employee employee) {
        return balances.findByEmployeeId(employee.getId()).stream()
                .map(LeaveBalanceResponse::from)
                .toList();
    }

    /** The signed-in employee's leave requests, newest first. */
    public List<LeaveRequestResponse> myRequests(Employee employee) {
        return requests.findByEmployeeIdOrderByCreatedAtDesc(employee.getId()).stream()
                .map(LeaveRequestResponse::from)
                .toList();
    }

    /** Submit a new leave request (PENDING). Balance is only deducted on approval. */
    @Transactional
    public LeaveRequestResponse apply(Employee employee, ApplyLeaveRequest req) {
        if (req.endDate().isBefore(req.startDate())) {
            throw new BadRequestException("End date cannot be before start date");
        }
        int days = inclusiveDays(req);

        LeaveRequest entity = LeaveRequest.builder()
                .employee(employee)
                .leaveType(req.leaveType())
                .startDate(req.startDate())
                .endDate(req.endDate())
                .days(days)
                .reason(req.reason())
                .status(LeaveStatus.PENDING)
                .build();
        entity.setCompany(employee.getCompany());

        LeaveRequest saved = requests.save(entity);
        notifier.notifyApprover(employee.getId(), employee.getCompany().getId(), NotificationType.APPROVAL_REQUEST,
                "New leave request", employee.getFullName() + " requested " + req.leaveType().label() + " (" + days + " day" + (days == 1 ? "" : "s") + ")");
        return LeaveRequestResponse.from(saved);
    }

    /**
     * Pending requests the caller is responsible for. HR_ADMIN / OWNER see every
     * pending request in the company (override); a MANAGER sees only requests
     * from their direct reports.
     */
    public List<PendingLeaveResponse> pending(Employee caller) {
        boolean override = caller.getRole() == Role.HR_ADMIN || caller.getRole() == Role.OWNER;
        List<LeaveRequest> rows = override
                ? requests.findByStatusAndCompanyId(LeaveStatus.PENDING, caller.getCompany().getId())
                : requests.findPendingForReportingManager(LeaveStatus.PENDING, caller.getCompany().getId(), caller.getId());
        return rows.stream().map(r -> PendingLeaveResponse.from(r, remainingFor(r))).toList();
    }

    /** Approve a pending request and deduct the matching balance, if any. */
    @Transactional
    public void approve(UUID id, Employee admin) {
        LeaveRequest r = mustBePending(id, admin);
        r.setStatus(LeaveStatus.APPROVED);
        r.setDecidedBy(admin);
        r.setDecidedAt(Instant.now());

        balances.findByEmployeeIdAndLeaveType(r.getEmployee().getId(), r.getLeaveType())
                .ifPresent(b -> {
                    int used = b.getUsed() == null ? 0 : b.getUsed();
                    b.setUsed(used + (r.getDays() == null ? 0 : r.getDays()));
                });
        notifier.notifyRequester(r.getEmployee(), NotificationType.LEAVE_APPROVED,
                "Leave approved", "Your " + r.getLeaveType().label() + " was approved.");
    }

    /** Reject a pending request. */
    @Transactional
    public void reject(UUID id, Employee admin) {
        LeaveRequest r = mustBePending(id, admin);
        r.setStatus(LeaveStatus.REJECTED);
        r.setDecidedBy(admin);
        r.setDecidedAt(Instant.now());
        notifier.notifyRequester(r.getEmployee(), NotificationType.LEAVE_REJECTED,
                "Leave declined", "Your " + r.getLeaveType().label() + " was declined.");
    }

    private LeaveRequest mustBePending(UUID id, Employee admin) {
        LeaveRequest r = requests.findByIdWithEmployeeAndManager(id)
                .orElseThrow(() -> ResourceNotFoundException.of("LeaveRequest", id));
        // Tenant isolation: never decide (or leak) another company's request.
        if (!r.getCompany().getId().equals(admin.getCompany().getId())) {
            throw ResourceNotFoundException.of("LeaveRequest", id);
        }
        if (r.getStatus() != LeaveStatus.PENDING) {
            throw new BadRequestException("Only pending requests can be decided");
        }
        assertCanDecide(r, admin);
        return r;
    }

    /** HR_ADMIN/OWNER decide anything; a MANAGER decides only their direct reports' requests. */
    private void assertCanDecide(LeaveRequest r, Employee caller) {
        if (caller.getRole() == Role.HR_ADMIN || caller.getRole() == Role.OWNER) {
            return;
        }
        Employee mgr = r.getEmployee().getReportingManager();
        if (mgr == null || !mgr.getId().equals(caller.getId())) {
            throw new AccessDeniedException("You can only decide requests from your direct reports");
        }
    }

    private Integer remainingFor(LeaveRequest r) {
        return balances.findByEmployeeIdAndLeaveType(r.getEmployee().getId(), r.getLeaveType())
                .map(LeaveBalance::remaining)
                .orElse(null);
    }

    private int inclusiveDays(ApplyLeaveRequest req) {
        return (int) (ChronoUnit.DAYS.between(req.startDate(), req.endDate()) + 1);
    }
}
