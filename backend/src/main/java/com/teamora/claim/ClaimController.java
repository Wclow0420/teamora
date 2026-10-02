package com.teamora.claim;

import com.teamora.claim.dto.ClaimResponse;
import com.teamora.claim.dto.ClaimSummaryResponse;
import com.teamora.claim.dto.PendingClaimResponse;
import com.teamora.claim.dto.SubmitClaimRequest;
import com.teamora.common.PhotoCodec;
import com.teamora.security.CurrentEmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.CacheControl;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;

@RestController
@RequiredArgsConstructor
public class ClaimController {

    private final ClaimService claimService;
    private final CurrentEmployeeService currentEmployee;

    /** Current employee's Claims screen: totals + list. */
    @GetMapping("/api/claims")
    public ClaimSummaryResponse mySummary() {
        return claimService.mySummary(currentEmployee.require());
    }

    /** Submit a new claim. */
    @PostMapping("/api/claims")
    public ClaimResponse submit(@Valid @RequestBody SubmitClaimRequest req) {
        return claimService.submit(currentEmployee.require(), req);
    }

    /**
     * Stream a claim's receipt photo. Access (enforced in the service): the
     * claim's owner or its approver; 403 otherwise, 404 if the claim is in
     * another company or has no receipt.
     */
    @GetMapping("/api/claims/{id}/receipt")
    public ResponseEntity<byte[]> receipt(@PathVariable UUID id) {
        PhotoCodec.Photo photo = claimService.getReceipt(currentEmployee.require(), id);
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(photo.contentType()))
                .cacheControl(CacheControl.maxAge(1, TimeUnit.DAYS).cachePrivate())
                .body(photo.bytes());
    }

    /** Admin approvals queue (ADMIN only via /api/admin/** security rule). */
    @GetMapping("/api/admin/claims")
    public List<PendingClaimResponse> pending(@RequestParam(defaultValue = "PENDING") ClaimStatus status) {
        // Routed to the caller: HR_ADMIN/OWNER see all pending; a MANAGER sees
        // only their direct reports' claims.
        return claimService.pending(currentEmployee.require());
    }

    @PostMapping("/api/admin/claims/{id}/approve")
    public ClaimResponse approve(@PathVariable UUID id) {
        return claimService.approve(id, currentEmployee.require());
    }

    @PostMapping("/api/admin/claims/{id}/reject")
    public ClaimResponse reject(@PathVariable UUID id) {
        return claimService.reject(id, currentEmployee.require());
    }
}
