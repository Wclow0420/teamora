package com.teamora.company;

import com.teamora.company.dto.CompanySettingsDtos.CompanySettingsResponse;
import com.teamora.company.dto.CompanySettingsDtos.UpdateCompanySettingsRequest;
import com.teamora.security.CurrentEmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/admin/company-settings")
@RequiredArgsConstructor
public class CompanySettingsController {

    private final CompanySettingsService companySettingsService;
    private final CurrentEmployeeService currentEmployee;

    /** The caller's company payroll/schedule defaults. Admin (OWNER/HR_ADMIN/MANAGER) via /api/admin/**. */
    @GetMapping
    public CompanySettingsResponse get() {
        return CompanySettingsResponse.from(
                companySettingsService.getOrCreate(currentEmployee.require().getCompany()));
    }

    /** Update the company defaults — owners / HR admins only. */
    @PatchMapping
    @PreAuthorize("hasAnyRole('OWNER','HR_ADMIN')")
    public CompanySettingsResponse update(@Valid @RequestBody UpdateCompanySettingsRequest req) {
        return CompanySettingsResponse.from(
                companySettingsService.update(currentEmployee.require().getCompany(), req));
    }
}
