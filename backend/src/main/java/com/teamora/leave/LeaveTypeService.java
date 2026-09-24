package com.teamora.leave;

import com.teamora.common.exception.BadRequestException;
import com.teamora.common.exception.ResourceNotFoundException;
import com.teamora.company.Company;
import com.teamora.leave.dto.LeaveTypeDtos.CreateLeaveTypeRequest;
import com.teamora.leave.dto.LeaveTypeDtos.LeaveTypeResponse;
import com.teamora.leave.dto.LeaveTypeDtos.UpdateLeaveTypeRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Locale;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaveTypeService {

    /** The default catalogue seeded for every company. Keep in sync with V11's seed. */
    private record Seed(String name, String code, boolean paid, int entitlement, LeaveAccrual accrual, String color, int sort) {}

    private static final List<Seed> DEFAULTS = List.of(
            new Seed("Annual Leave", "ANNUAL", true, 16, LeaveAccrual.FIXED_ANNUAL, "coral", 1),
            new Seed("Medical Leave", "MEDICAL", true, 14, LeaveAccrual.FIXED_ANNUAL, "sage", 2),
            new Seed("Emergency Leave", "EMERGENCY", true, 5, LeaveAccrual.FIXED_ANNUAL, "amber", 3),
            new Seed("Unpaid Leave", "UNPAID", false, 0, LeaveAccrual.NONE, "violet", 4));

    private final LeaveTypeRepository leaveTypes;

    // ---------- reads ----------

    /** Types for staff pickers (active only) or the admin catalogue (all). */
    public List<LeaveTypeResponse> list(UUID companyId, boolean includeInactive) {
        List<LeaveType> rows = includeInactive
                ? leaveTypes.findByCompanyIdOrderBySortOrderAscNameAsc(companyId)
                : leaveTypes.findByCompanyIdAndActiveTrueOrderBySortOrderAscNameAsc(companyId);
        return rows.stream().map(LeaveTypeResponse::from).toList();
    }

    /** Resolve a leave type by id within a company, or 404. */
    public LeaveType requireInCompany(UUID companyId, UUID id) {
        return leaveTypes.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> ResourceNotFoundException.of("LeaveType", id));
    }

    // ---------- writes ----------

    @Transactional
    public LeaveTypeResponse create(Company company, CreateLeaveTypeRequest req) {
        String code = normaliseCode(req.code() != null && !req.code().isBlank() ? req.code() : req.name());
        if (code.isBlank()) {
            throw new BadRequestException("A leave type code is required");
        }
        if (leaveTypes.existsByCompanyIdAndCode(company.getId(), code)) {
            throw new BadRequestException("A leave type with code '" + code + "' already exists");
        }
        LeaveType t = LeaveType.builder()
                .name(req.name().trim())
                .code(code)
                .paid(req.paid() == null || req.paid())
                .defaultEntitlementDays(req.defaultEntitlementDays() != null ? req.defaultEntitlementDays() : 0)
                .accrual(req.accrual() != null ? req.accrual() : LeaveAccrual.FIXED_ANNUAL)
                .carryForwardMaxDays(req.carryForwardMaxDays() != null
                        ? req.carryForwardMaxDays().setScale(2, java.math.RoundingMode.HALF_UP)
                        : java.math.BigDecimal.ZERO.setScale(2))
                .colorKey(req.colorKey() != null && !req.colorKey().isBlank() ? req.colorKey().trim() : "coral")
                .active(req.active() == null || req.active())
                .sortOrder(req.sortOrder() != null ? req.sortOrder() : nextSortOrder(company.getId()))
                .build();
        t.setCompany(company);
        return LeaveTypeResponse.from(leaveTypes.save(t));
    }

    @Transactional
    public LeaveTypeResponse update(UUID companyId, UUID id, UpdateLeaveTypeRequest req) {
        LeaveType t = requireInCompany(companyId, id);
        if (req.name() != null && !req.name().isBlank()) t.setName(req.name().trim());
        if (req.paid() != null) t.setPaid(req.paid());
        if (req.defaultEntitlementDays() != null) t.setDefaultEntitlementDays(req.defaultEntitlementDays());
        if (req.accrual() != null) t.setAccrual(req.accrual());
        if (req.carryForwardMaxDays() != null) {
            t.setCarryForwardMaxDays(req.carryForwardMaxDays().setScale(2, java.math.RoundingMode.HALF_UP));
        }
        if (req.colorKey() != null && !req.colorKey().isBlank()) t.setColorKey(req.colorKey().trim());
        if (req.active() != null) t.setActive(req.active());
        if (req.sortOrder() != null) t.setSortOrder(req.sortOrder());
        return LeaveTypeResponse.from(t);
    }

    // ---------- seeding ----------

    /** Seed the default catalogue for a company (idempotent; only adds missing codes). */
    @Transactional
    public void seedDefaults(Company company) {
        for (Seed s : DEFAULTS) {
            if (leaveTypes.existsByCompanyIdAndCode(company.getId(), s.code())) {
                continue;
            }
            LeaveType t = LeaveType.builder()
                    .name(s.name())
                    .code(s.code())
                    .paid(s.paid())
                    .defaultEntitlementDays(s.entitlement())
                    .accrual(s.accrual())
                    .carryForwardMaxDays(java.math.BigDecimal.ZERO.setScale(2))
                    .colorKey(s.color())
                    .active(true)
                    .sortOrder(s.sort())
                    .build();
            t.setCompany(company);
            leaveTypes.save(t);
        }
    }

    // ---------- helpers ----------

    private int nextSortOrder(UUID companyId) {
        return (int) leaveTypes.countByCompanyId(companyId) + 1;
    }

    private static String normaliseCode(String raw) {
        return raw.trim().toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]+", "_").replaceAll("(^_|_$)", "");
    }
}
