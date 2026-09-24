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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaveService {

    private final LeaveBalanceRepository balances;
    private final LeaveRequestRepository requests;
    private final LeaveTypeService leaveTypes;
    private final LeaveDurationCalculator durations;
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

    /**
     * Submit a new leave request (PENDING). Balance is only deducted on approval.
     *
     * <p>The day count is the <b>working-day fraction</b> resolved by
     * {@link LeaveDurationCalculator} — full days skip rest days and public holidays,
     * a half day is 0.50 and an hourly request is {@code hours / hoursPerDay}.
     */
    @Transactional
    public LeaveRequestResponse apply(Employee employee, ApplyLeaveRequest req) {
        LeaveDurationCalculator.Duration duration = durations.resolve(
                employee, req.unit(), req.startDate(), req.endDate(), req.halfDayPeriod(), req.hours());

        LeaveType type = leaveTypes.requireInCompany(employee.getCompany().getId(), req.leaveTypeId());

        LeaveRequest entity = LeaveRequest.builder()
                .employee(employee)
                .leaveType(type)
                .startDate(req.startDate())
                .endDate(req.endDate())
                .days(duration.days())
                .durationUnit(duration.unit())
                .halfDayPeriod(duration.halfDayPeriod())
                .hours(duration.hours())
                .startTime(duration.unit() == LeaveDurationUnit.HOURS ? req.startTime() : null)
                .reason(req.reason())
                .status(LeaveStatus.PENDING)
                .build();
        entity.setCompany(employee.getCompany());

        LeaveRequest saved = requests.save(entity);
        LeaveRequestResponse response = LeaveRequestResponse.from(saved);
        notifier.notifyApprover(employee.getId(), employee.getCompany().getId(), NotificationType.APPROVAL_REQUEST,
                "New leave request",
                employee.getFullName() + " requested " + type.label() + " (" + response.durationLabel() + ")");
        return response;
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

        balances.findByEmployeeIdAndLeaveTypeId(r.getEmployee().getId(), r.getLeaveType().getId())
                .ifPresent(b -> {
                    BigDecimal taken = r.getDays() == null ? BigDecimal.ZERO : r.getDays();
                    b.setUsed(b.usedOrZero().add(taken));
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

    private BigDecimal remainingFor(LeaveRequest r) {
        return balances.findByEmployeeIdAndLeaveTypeId(r.getEmployee().getId(), r.getLeaveType().getId())
                .map(LeaveBalance::remaining)
                .orElse(null);
    }
}
