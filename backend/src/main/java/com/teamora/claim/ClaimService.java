package com.teamora.claim;

import com.teamora.claim.dto.ClaimResponse;
import com.teamora.claim.dto.ClaimSummaryResponse;
import com.teamora.claim.dto.PendingClaimResponse;
import com.teamora.claim.dto.SubmitClaimRequest;
import com.teamora.common.exception.BadRequestException;
import com.teamora.common.exception.ResourceNotFoundException;
import com.teamora.employee.Employee;
import com.teamora.employee.Role;
import com.teamora.notification.ApprovalNotifier;
import com.teamora.notification.NotificationType;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ClaimService {

    private final ClaimRepository claims;
    private final ApprovalNotifier notifier;

    /** Staff Claims screen: pending total, reimbursed-this-month, and the full list. */
    public ClaimSummaryResponse mySummary(Employee employee) {
        List<Claim> mine = claims.findByEmployeeIdOrderByClaimDateDesc(employee.getId());

        BigDecimal pendingTotal = mine.stream()
                .filter(c -> c.getStatus() == ClaimStatus.PENDING)
                .map(Claim::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate monthStart = LocalDate.now().withDayOfMonth(1);
        LocalDate nextMonth = monthStart.plusMonths(1);
        BigDecimal reimbursed = mine.stream()
                .filter(c -> c.getStatus() == ClaimStatus.APPROVED)
                .filter(c -> !c.getClaimDate().isBefore(monthStart) && c.getClaimDate().isBefore(nextMonth))
                .map(Claim::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        List<ClaimResponse> list = mine.stream().map(ClaimResponse::from).toList();
        return new ClaimSummaryResponse(money(pendingTotal), money(reimbursed), list);
    }

    /** Submit a new claim in PENDING state. */
    @Transactional
    public ClaimResponse submit(Employee employee, SubmitClaimRequest req) {
        Claim claim = Claim.builder()
                .employee(employee)
                .category(req.category())
                .title(req.title())
                .amount(req.amount().setScale(2, RoundingMode.HALF_UP))
                .claimDate(req.claimDate())
                .status(ClaimStatus.PENDING)
                .receiptUrl(req.receiptUrl())
                .build();
        claim.setCompany(employee.getCompany());
        Claim saved = claims.save(claim);
        notifier.notifyApprover(employee.getId(), employee.getCompany().getId(), NotificationType.APPROVAL_REQUEST,
                "New claim", employee.getFullName() + " submitted a claim · RM " + money(saved.getAmount()));
        return ClaimResponse.from(saved);
    }

    /**
     * Approvals queue routed to the caller. HR_ADMIN / OWNER see every pending
     * claim in the company; a MANAGER sees only their direct reports'.
     */
    public List<PendingClaimResponse> pending(Employee caller) {
        boolean override = caller.getRole() == Role.HR_ADMIN || caller.getRole() == Role.OWNER;
        List<Claim> rows = override
                ? claims.findByStatusAndCompanyId(ClaimStatus.PENDING, caller.getCompany().getId())
                : claims.findPendingForReportingManager(ClaimStatus.PENDING, caller.getCompany().getId(), caller.getId());
        return rows.stream().map(PendingClaimResponse::from).toList();
    }

    @Transactional
    public ClaimResponse approve(UUID id, Employee admin) {
        return decide(id, admin, ClaimStatus.APPROVED);
    }

    @Transactional
    public ClaimResponse reject(UUID id, Employee admin) {
        return decide(id, admin, ClaimStatus.REJECTED);
    }

    private ClaimResponse decide(UUID id, Employee admin, ClaimStatus target) {
        Claim claim = claims.findByIdWithEmployeeAndManager(id)
                .orElseThrow(() -> ResourceNotFoundException.of("Claim", id));
        if (!claim.getCompany().getId().equals(admin.getCompany().getId())) {
            // Cross-tenant access: treat as not found rather than leaking existence.
            throw ResourceNotFoundException.of("Claim", id);
        }
        if (claim.getStatus() != ClaimStatus.PENDING) {
            throw new BadRequestException("Only a pending claim can be " + target.name().toLowerCase());
        }
        assertCanDecide(claim, admin);
        claim.setStatus(target);
        claim.setDecidedBy(admin);
        claim.setDecidedAt(Instant.now());
        boolean approved = target == ClaimStatus.APPROVED;
        notifier.notifyRequester(claim.getEmployee(),
                approved ? NotificationType.CLAIM_APPROVED : NotificationType.CLAIM_REJECTED,
                approved ? "Claim approved" : "Claim declined",
                "Your claim '" + claim.getTitle() + "' (RM " + money(claim.getAmount()) + ") was " + (approved ? "approved." : "declined."));
        return ClaimResponse.from(claims.save(claim));
    }

    /** HR_ADMIN/OWNER decide anything; a MANAGER decides only their direct reports' claims. */
    private void assertCanDecide(Claim claim, Employee caller) {
        if (caller.getRole() == Role.HR_ADMIN || caller.getRole() == Role.OWNER) {
            return;
        }
        Employee mgr = claim.getEmployee().getReportingManager();
        if (mgr == null || !mgr.getId().equals(caller.getId())) {
            throw new AccessDeniedException("You can only decide claims from your direct reports");
        }
    }

    private static String money(BigDecimal value) {
        return value.setScale(2, RoundingMode.HALF_UP).toPlainString();
    }
}
