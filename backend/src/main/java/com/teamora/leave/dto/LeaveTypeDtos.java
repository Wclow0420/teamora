package com.teamora.leave.dto;

import com.teamora.leave.LeaveAccrual;
import com.teamora.leave.LeaveType;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

/** Payloads for the configurable leave-type catalogue. */
public final class LeaveTypeDtos {

    private LeaveTypeDtos() {}

    public record LeaveTypeResponse(
            java.util.UUID id,
            String name,
            String code,
            boolean paid,
            int defaultEntitlementDays,
            LeaveAccrual accrual,
            java.math.BigDecimal carryForwardMaxDays,
            String colorKey,
            boolean active,
            int sortOrder
    ) {
        public static LeaveTypeResponse from(LeaveType t) {
            return new LeaveTypeResponse(
                    t.getId(), t.getName(), t.getCode(), t.isPaid(),
                    t.getDefaultEntitlementDays(), t.getAccrual(), t.carryForwardMaxDaysOrZero(),
                    t.getColorKey(), t.isActive(), t.getSortOrder());
        }
    }

    /** Create a new leave type. {@code code} is optional — derived from the name if omitted. */
    public record CreateLeaveTypeRequest(
            @NotBlank @Size(max = 64) String name,
            @Size(max = 24) String code,
            Boolean paid,
            @PositiveOrZero Integer defaultEntitlementDays,
            LeaveAccrual accrual,
            @PositiveOrZero @DecimalMax("999.99") java.math.BigDecimal carryForwardMaxDays,
            @Size(max = 16) String colorKey,
            Boolean active,
            Integer sortOrder
    ) {}

    /** Partial update — any null field is left unchanged. {@code code} is immutable. */
    public record UpdateLeaveTypeRequest(
            @Size(max = 64) String name,
            Boolean paid,
            @PositiveOrZero Integer defaultEntitlementDays,
            LeaveAccrual accrual,
            @PositiveOrZero @DecimalMax("999.99") java.math.BigDecimal carryForwardMaxDays,
            @Size(max = 16) String colorKey,
            Boolean active,
            Integer sortOrder
    ) {}
}
