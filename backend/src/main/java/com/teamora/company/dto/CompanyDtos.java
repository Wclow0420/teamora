package com.teamora.company.dto;

import jakarta.validation.constraints.Size;
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
            @Size(max = 150, message = "Company name must be at most 150 characters") String name,
            @Size(max = 64, message = "Registration number must be at most 64 characters") String registrationNo,
            @Size(max = 64, message = "EPF number must be at most 64 characters") String epfNo,
            @Size(max = 64, message = "SOCSO number must be at most 64 characters") String socsoNo,
            @Size(max = 254, message = "Email must be at most 254 characters") String email,
            @Size(max = 32, message = "Phone number must be at most 32 characters") String phone,
            @Size(max = 500, message = "Address must be at most 500 characters") String address
    ) {}

    /**
     * Owner-only "delete company and all data". {@code password} is the owner's
     * current password; {@code confirmName} must equal the company name
     * (case-insensitive, trimmed). Both are checked in the service so the app gets
     * one plain-language message per field rather than a validation map.
     */
    public record DeleteCompanyRequest(
            @Size(max = 128) String password,
            @Size(max = 255) String confirmName
    ) {}
}
