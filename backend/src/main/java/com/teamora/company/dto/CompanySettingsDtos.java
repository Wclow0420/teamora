package com.teamora.company.dto;

import com.teamora.company.CompanySettings;
import com.teamora.employee.PayBasis;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;

import java.math.BigDecimal;
import java.time.format.DateTimeFormatter;

/** Payloads for the company payroll/schedule defaults. */
public final class CompanySettingsDtos {

    private CompanySettingsDtos() {}

    public record CompanySettingsResponse(
            PayBasis defaultPayBasis,
            int defaultWorkingDays,
            BigDecimal defaultHoursPerDay,
            int leaveYearStartMonth,
            /** When the working day starts, 24h "HH:mm" (e.g. "09:00"). */
            String workStartTime,
            /** Minutes after the start time before a clock-in counts as late (0–120). */
            int lateGraceMinutes
    ) {
        private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

        public static CompanySettingsResponse from(CompanySettings s) {
            return new CompanySettingsResponse(
                    s.getDefaultPayBasis(),
                    s.getDefaultWorkingDays(),
                    s.getDefaultHoursPerDay(),
                    s.getLeaveYearStartMonth(),
                    s.workStartTimeOrDefault().format(HH_MM),
                    s.getLateGraceMinutes());
        }
    }

    /** Partial update — any null field is left unchanged. */
    public record UpdateCompanySettingsRequest(
            PayBasis defaultPayBasis,
            @Min(value = 1, message = "Pick at least one working day")
            @Max(127) Integer defaultWorkingDays,
            @DecimalMin("0.5") @DecimalMax("24.0") BigDecimal defaultHoursPerDay,
            /** Calendar month the leave year starts on (1 = January = calendar year). */
            @Min(1) @Max(12) Integer leaveYearStartMonth,
            /** Work start time, 24h "HH:mm". */
            @Pattern(regexp = "^([01]\\d|2[0-3]):[0-5]\\d$", message = "Work start time must be HH:mm (24-hour)")
            String workStartTime,
            @Min(value = 0, message = "Late grace must be 0 to 120 minutes")
            @Max(value = 120, message = "Late grace must be 0 to 120 minutes")
            Integer lateGraceMinutes
    ) {}
}
