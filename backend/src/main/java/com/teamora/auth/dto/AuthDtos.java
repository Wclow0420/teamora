package com.teamora.auth.dto;

import com.teamora.employee.dto.EmployeeResponse;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** Request/response payloads for the auth endpoints. */
public final class AuthDtos {

    private AuthDtos() {}

    public record LoginRequest(
            @Email @NotBlank @Size(max = 254) String email,
            @NotBlank @Size(max = 128) String password
    ) {}

    /** Self-serve signup: creates a new company + its OWNER account. */
    public record RegisterRequest(
            @NotBlank @Size(max = 150, message = "Company name must be at most 150 characters") String companyName,
            @NotBlank @Size(max = 100, message = "Name must be at most 100 characters") String fullName,
            @Email @NotBlank @Size(max = 254, message = "Email must be at most 254 characters") String email,
            @NotBlank(message = "Enter a password")
            @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters") String password
    ) {}

    public record RefreshRequest(@NotBlank @Size(max = 200) String refreshToken) {}

    public record LogoutRequest(@NotBlank @Size(max = 200) String refreshToken) {}

    /**
     * Change the caller's own password. {@code refreshToken} is optional: when
     * it is the caller's current refresh token, that one session is kept and
     * every other session is signed out; when absent, all sessions are revoked.
     */
    public record ChangePasswordRequest(
            @NotBlank(message = "Enter your current password") @Size(max = 128) String currentPassword,
            @NotBlank(message = "Enter a new password")
            @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters") String newPassword,
            @Size(max = 200) String refreshToken
    ) {}

    /** Ask for a one-time password-reset code. Always answered with 200. */
    public record ForgotPasswordRequest(@NotBlank(message = "Enter your email") @Size(max = 254) String email) {}

    /** {@code devCode} is the code itself in local dev only; null everywhere else. */
    public record ForgotPasswordResponse(String devCode) {}

    /** Redeem a reset code and choose a new password. */
    public record ResetPasswordRequest(
            @NotBlank(message = "Enter your email") @Size(max = 254) String email,
            @NotBlank(message = "Enter the 6-digit code") @Size(max = 12) String code,
            @NotBlank(message = "Enter a new password")
            @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters") String newPassword
    ) {}

    public record AuthResponse(
            String accessToken,
            String refreshToken,
            String tokenType,
            long expiresIn,
            EmployeeResponse employee
    ) {}
}
