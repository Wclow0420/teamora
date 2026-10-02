package com.teamora.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Strongly-typed binding for the {@code teamora.*} config tree. */
@ConfigurationProperties(prefix = "teamora")
public record TeamoraProperties(
        boolean seed,
        Jwt jwt,
        Cors cors,
        PasswordReset passwordReset
) {
    /**
     * {@code exposeCode}: LOCAL DEV / TEST ONLY — return the one-time reset code in
     * the forgot-password response so the flow is testable without an email provider.
     */
    public record PasswordReset(boolean exposeCode) {}

    /** True only when the reset code may be echoed back to the caller (never in production). */
    public boolean exposeResetCode() {
        return passwordReset != null && passwordReset.exposeCode();
    }

    public record Jwt(
            String secret,
            long accessTokenTtlMinutes,
            long refreshTokenTtlDays
    ) {}

    public record Cors(String allowedOrigins) {}
}
