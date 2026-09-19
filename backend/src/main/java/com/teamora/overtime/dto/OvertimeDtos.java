package com.teamora.overtime.dto;

import com.teamora.overtime.OvertimeRequest;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.format.TextStyle;
import java.util.Locale;
import java.util.UUID;

public final class OvertimeDtos {

    private OvertimeDtos() {}

    /** Submit overtime: a date, hours (0.5–24), and an optional reason. */
    public record SubmitOvertimeRequest(
            @NotNull LocalDate workDate,
            @NotNull @DecimalMin("0.5") @DecimalMax("24.0") BigDecimal hours,
            String reason
    ) {}

    /** The employee's own overtime row. */
    public record OvertimeResponse(
            UUID id,
            String workDateLabel,
            String hoursLabel,
            String reason,
            String status,
            String statusLabel
    ) {
        public static OvertimeResponse from(OvertimeRequest o) {
            return new OvertimeResponse(
                    o.getId(), fmtDate(o.getWorkDate()), fmtHours(o.getHours()),
                    o.getReason(), o.getStatus().name(), o.getStatus().label());
        }
    }

    /** A pending overtime row for an approver. */
    public record PendingOvertimeResponse(
            UUID id,
            String employeeName,
            String initial,
            String workDateLabel,
            String hoursLabel,
            String reason,
            String accentColorKey
    ) {
        public static PendingOvertimeResponse from(OvertimeRequest o) {
            String name = o.getEmployee().getFullName();
            return new PendingOvertimeResponse(
                    o.getId(), name, name.substring(0, 1).toUpperCase(),
                    fmtDate(o.getWorkDate()), fmtHours(o.getHours()), o.getReason(), "violet");
        }
    }

    static String fmtDate(LocalDate d) {
        return d.getDayOfMonth() + " " + d.getMonth().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
    }

    static String fmtHours(BigDecimal h) {
        return h.stripTrailingZeros().toPlainString() + "h";
    }
}
