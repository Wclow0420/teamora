package com.teamora.company;

import com.teamora.company.dto.CompanyDtos.UpdateCompanyRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.Locale;

@Service
@RequiredArgsConstructor
public class CompanyService {

    private final CompanyRepository companies;

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
    public Company update(Company company, UpdateCompanyRequest req) {
        if (req.name() != null && !req.name().isBlank()) company.setName(req.name().trim());
        if (req.registrationNo() != null) company.setRegistrationNo(req.registrationNo());
        if (req.epfNo() != null) company.setEpfNo(req.epfNo());
        if (req.socsoNo() != null) company.setSocsoNo(req.socsoNo());
        if (req.email() != null) company.setEmail(req.email());
        if (req.phone() != null) company.setPhone(req.phone());
        if (req.address() != null) company.setAddress(req.address());
        return company;
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
