package com.teamora.company;

import com.teamora.company.dto.CompanySettingsDtos.UpdateCompanySettingsRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CompanySettingsService {

    private final CompanySettingsRepository settings;

    /** The company's settings, creating (and persisting) defaults if none exist yet. */
    @Transactional
    public CompanySettings getOrCreate(Company company) {
        return settings.findById(company.getId())
                .orElseGet(() -> settings.save(CompanySettings.defaultsFor(company)));
    }

    /** Seed the default settings row for a brand-new company (idempotent). */
    @Transactional
    public CompanySettings createDefaults(Company company) {
        if (settings.existsById(company.getId())) {
            return settings.findById(company.getId()).orElseThrow();
        }
        return settings.save(CompanySettings.defaultsFor(company));
    }

    /** Read-only fetch of a company's settings, falling back to transient defaults. */
    public CompanySettings resolve(Company company) {
        return settings.findById(company.getId())
                .orElseGet(() -> CompanySettings.defaultsFor(company));
    }

    @Transactional
    public CompanySettings update(Company company, UpdateCompanySettingsRequest req) {
        CompanySettings s = getOrCreate(company);
        if (req.defaultPayBasis() != null) {
            s.setDefaultPayBasis(req.defaultPayBasis());
        }
        if (req.defaultWorkingDays() != null) {
            s.setDefaultWorkingDays(req.defaultWorkingDays().shortValue());
        }
        if (req.defaultHoursPerDay() != null) {
            s.setDefaultHoursPerDay(req.defaultHoursPerDay());
        }
        if (req.leaveYearStartMonth() != null) {
            s.setLeaveYearStartMonth(req.leaveYearStartMonth());
        }
        return s;
    }
}
