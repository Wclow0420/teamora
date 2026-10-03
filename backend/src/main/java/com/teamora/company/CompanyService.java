package com.teamora.company;

import com.teamora.common.exception.BadRequestException;
import com.teamora.company.dto.CompanyDtos.DeleteCompanyRequest;
import com.teamora.company.dto.CompanyDtos.UpdateCompanyRequest;
import com.teamora.employee.Employee;
import com.teamora.employee.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companies;
    private final CompanyPurgeService purgeService;
    private final PasswordEncoder passwordEncoder;

    /** Create a new tenant. Generates a unique slug from the name. */
    @Transactional
    public Company create(String name) {
        Company c = Company.builder()
                .name(name.trim())
                .slug(uniqueSlug(name))
                .timezone("Asia/Kuala_Lumpur")
                .currency("MYR")
                .active(true)
                .build();
        return companies.save(c);
    }

    @Transactional
    public Company update(Company caller, UpdateCompanyRequest req) {
        // The caller's company comes from the security context, i.e. outside this
        // transaction — editing it in place is never flushed. Load a managed copy.
        Company company = companies.findById(caller.getId()).orElseThrow();
        if (req.name() != null) {
            if (req.name().isBlank()) {
                throw new BadRequestException("Company name can't be blank");
            }
            company.setName(req.name().trim());
        }
        if (req.registrationNo() != null) company.setRegistrationNo(req.registrationNo());
        if (req.epfNo() != null) company.setEpfNo(req.epfNo());
        if (req.socsoNo() != null) company.setSocsoNo(req.socsoNo());
        if (req.email() != null) company.setEmail(req.email());
        if (req.phone() != null) company.setPhone(req.phone());
        if (req.address() != null) company.setAddress(req.address());
        return company;
    }

    /**
     * Owner-only: permanently erase the caller's company and every row that
     * belongs to it (staff accounts, attendance + selfies, leave, claims +
     * receipts, overtime, payroll, schedule, calendar, notifications, sessions…).
     * No soft delete, no grace period — the password + typed company name are the
     * safeguards. All-or-nothing: one transaction.
     */
    @Transactional
    public void delete(Employee caller, DeleteCompanyRequest req) {
        if (caller.getRole() != Role.OWNER) {
            throw new AccessDeniedException("Only the owner can delete the company");
        }
        String password = req == null ? null : req.password();
        if (password == null || password.isEmpty() || !passwordEncoder.matches(password, caller.getPasswordHash())) {
            throw new BadRequestException("Password is incorrect");
        }
        Company company = caller.getCompany();
        String typed = req.confirmName() == null ? "" : req.confirmName().trim();
        if (typed.isEmpty() || !typed.equalsIgnoreCase(company.getName().trim())) {
            throw new BadRequestException("Type the company name exactly to confirm");
        }
        purgeService.purge(company.getId());
    }

    private String uniqueSlug(String name) {
        String base = slugify(name);
        String slug = base;
        int n = 2;
        while (companies.existsBySlug(slug)) {
            slug = base + "-" + n++;
        }
        return slug;
    }

    public static String slugify(String input) {
        String normalized = Normalizer.normalize(input, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "")
                .toLowerCase(Locale.ROOT)
                .replaceAll("[^a-z0-9]+", "-")
                .replaceAll("(^-|-$)", "");
        return normalized.isBlank() ? "company" : normalized;
    }
}
