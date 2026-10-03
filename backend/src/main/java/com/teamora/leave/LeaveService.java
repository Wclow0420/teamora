package com.teamora.leave;

import com.teamora.common.DeclineRequest;
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
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaveService {

    private final LeaveBalanceService leaveBalances;
    private final LeaveRequestRepository requests;
    private final LeaveTypeService leaveTypes;
    private final LeaveDurationCalculator durations;
    private final ApprovalNotifier notifier;

    /**
     * The signed-in employee's leave balances for {@code year} — the current leave
     * year when omitted. Missing rows are provisioned lazily (see
     * {@link LeaveBalanceService#ensureBalances}).
     */
    @Transactional
    public List<LeaveBalanceResponse> myBalances(Employee employee, Integer year) {
        return leaveBalances.balancesFor(employee, year);
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

        assertNoOverlap(employee, duration, req.startDate(), req.endDate());
        assertEnoughBalance(employee, type, duration.days(), req.startDate());

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
     * Reject a request that double-books dates the employee has already asked for
     * (PENDING) or been granted (APPROVED). Partial-day requests may share a day —
     * a morning and an afternoon half day, or hourly requests — but a full day
     * clashes with anything on the same date, and so do two half days in the same
     * period.
     */
    private void assertNoOverlap(Employee employee, LeaveDurationCalculator.Duration duration,
                                 LocalDate startDate, LocalDate endDate) {
        List<LeaveRequest> existing = requests.findOverlapping(employee.getId(),
                List.of(LeaveStatus.PENDING, LeaveStatus.APPROVED), startDate, endDate);
        for (LeaveRequest other : existing) {
            if (clashes(duration.unit(), duration.halfDayPeriod(), other)) {
                throw new BadRequestException("You already have a leave request covering these dates.");
            }
        }
    }

    private static boolean clashes(LeaveDurationUnit unit, HalfDayPeriod period, LeaveRequest other) {
        LeaveDurationUnit otherUnit = other.getDurationUnit() == null
                ? LeaveDurationUnit.FULL_DAY : other.getDurationUnit();
        if (unit == LeaveDurationUnit.FULL_DAY || otherUnit == LeaveDurationUnit.FULL_DAY) {
            return true;
        }
        if (unit == LeaveDurationUnit.HALF_DAY && otherUnit == LeaveDurationUnit.HALF_DAY) {
            // AM + PM coexist; the same half (or an unknown one) is a double booking.
            return period == null || other.getHalfDayPeriod() == null || period == other.getHalfDayPeriod();
        }
        return false;   // hourly alongside hourly / a half day
    }

    /**
     * For leave types that track an entitlement (accrual != NONE), the request must fit
     * in what is available right now in the leave year it starts in, less whatever is
     * already waiting for approval for that type and year. Untracked types (unpaid
     * leave etc.) are never blocked.
     */
    private void assertEnoughBalance(Employee employee, LeaveType type, BigDecimal requestedDays, LocalDate startDate) {
        if (type.getAccrual() == LeaveAccrual.NONE) {
            return;
        }
        int startMonth = leaveBalances.startMonth(employee.getCompany());
        int leaveYear = LeaveYear.yearOf(startDate, startMonth);
        BigDecimal remaining = leaveBalances.ensureBalance(employee, type, leaveYear)
                .available(startMonth, LocalDate.now(com.teamora.common.Zones.KL));

        BigDecimal pending = BigDecimal.ZERO;
        for (LeaveRequest r : requests.findByEmployeeAndTypeAndStatus(employee.getId(), type.getId(), LeaveStatus.PENDING)) {
            if (r.getDays() != null && LeaveYear.yearOf(r.getStartDate(), startMonth) == leaveYear) {
                pending = pending.add(r.getDays());
            }
        }

        BigDecimal available = remaining.subtract(pending).max(BigDecimal.ZERO);
        BigDecimal requested = requestedDays == null ? BigDecimal.ZERO : requestedDays;
        if (requested.compareTo(available) > 0) {
            throw new BadRequestException("Not enough " + type.label() + " balance — you have "
                    + plainDays(available) + " day(s) available.");
        }
    }

    /** 3.00 → "3", 0.50 → "0.5". */
    private static String plainDays(BigDecimal v) {
        BigDecimal stripped = v.stripTrailingZeros();
        return (stripped.scale() < 0 ? stripped.setScale(0) : stripped).toPlainString();
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

        // Charge the leave year the request STARTS in — leave booked in December for
        // January comes out of next year's entitlement, not this year's.
        int leaveYear = leaveBalances.leaveYearOf(r.getCompany(), r.getStartDate());
        LeaveBalance balance = leaveBalances.ensureBalance(r.getEmployee(), r.getLeaveType(), leaveYear);
        BigDecimal taken = r.getDays() == null ? BigDecimal.ZERO : r.getDays();
        balance.setUsed(balance.usedOrZero().add(taken));
        notifier.notifyRequester(r.getEmployee(), NotificationType.LEAVE_APPROVED,
                "Leave approved", "Your " + r.getLeaveType().label() + " was approved.");
    }

    /** Reject a pending request, optionally with a reason the requester will see. */
    @Transactional
    public void reject(UUID id, Employee admin, String decisionNote) {
        LeaveRequest r = mustBePending(id, admin);
        r.setStatus(LeaveStatus.REJECTED);
        r.setDecidedBy(admin);
        r.setDecidedAt(Instant.now());
        r.setDecisionNote(decisionNote);
        notifier.notifyRequester(r.getEmployee(), NotificationType.LEAVE_REJECTED,
                "Leave declined", "Your " + r.getLeaveType().label() + " was declined"
                        + DeclineRequest.declinedSuffix(decisionNote));
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

    /** Days available in the leave year the request falls in, or null when nothing is on record. */
    private BigDecimal remainingFor(LeaveRequest r) {
        return leaveBalances.availableOn(r.getEmployee(), r.getLeaveType().getId(), r.getStartDate());
    }
}
