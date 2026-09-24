package com.teamora.company.dto;

import com.teamora.company.CompanySettings;
import com.teamora.employee.PayBasis;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;

import java.math.BigDecimal;

/** Payloads for the company payroll/schedule defaults. */
public final class CompanySettingsDtos {

    private CompanySettingsDtos() {}

    public record CompanySettingsResponse(
            PayBasis defaultPayBasis,
            int defaultWorkingDays,
            BigDecimal defaultHoursPerDay,
            int leaveYearStartMonth
    ) {
        public static CompanySettingsResponse from(CompanySettings s) {
            return new CompanySettingsResponse(
                    s.getDefaultPayBasis(),
                    s.getDefaultWorkingDays(),
                    s.getDefaultHoursPerDay(),
                    s.getLeaveYearStartMonth());
        }
    }

    /** Partial update — any null field is left unchanged. */
    public record UpdateCompanySettingsRequest(
            PayBasis defaultPayBasis,
            @Min(0) @Max(127) Integer defaultWorkingDays,
            @DecimalMin("0.5") @DecimalMax("24.0") BigDecimal defaultHoursPerDay,
            /** Calendar month the leave year starts on (1 = January = calendar year). */
            @Min(1) @Max(12) Integer leaveYearStartMonth
    ) {}
}
