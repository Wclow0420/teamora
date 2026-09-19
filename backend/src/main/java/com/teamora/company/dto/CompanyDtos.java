package com.teamora.company.dto;

import com.teamora.company.Company;

import java.util.UUID;

/** Company request/response payloads. */
public final class CompanyDtos {

    private CompanyDtos() {}

    public record CompanyResponse(
            UUID id,
            String name,
            String slug,
            String registrationNo,
            String epfNo,
            String socsoNo,
            String email,
            String phone,
            String address,
            String timezone,
            String currency,
            boolean active
    ) {
        public static CompanyResponse from(Company c) {
            return new CompanyResponse(
                    c.getId(), c.getName(), c.getSlug(), c.getRegistrationNo(), c.getEpfNo(),
                    c.getSocsoNo(), c.getEmail(), c.getPhone(), c.getAddress(),
                    c.getTimezone(), c.getCurrency(), c.isActive());
        }
    }

    /** Partial update of the current company's profile. */
    public record UpdateCompanyRequest(
            String name,
            String registrationNo,
            String epfNo,
            String socsoNo,
            String email,
            String phone,
            String address
    ) {}
}
