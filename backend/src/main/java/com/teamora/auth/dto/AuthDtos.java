package com.teamora.auth.dto;

import com.teamora.employee.dto.EmployeeResponse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;

/** Request/response payloads for the auth endpoints. */
public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(
            @Email @NotBlank String email,
            @NotBlank String password
    ) {}

    /** Self-serve signup: creates a new company + its OWNER account. */
    public record RegisterRequest(
            @NotBlank String companyName,
            @NotBlank String fullName,
            @Email @NotBlank String email,
            @NotBlank String password
    ) {}

    public record RefreshRequest(@NotBlank String refreshToken) {}

    public record LogoutRequest(@NotBlank String refreshToken) {}

    public record AuthResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            EmployeeResponse employee
    ) {}
}
