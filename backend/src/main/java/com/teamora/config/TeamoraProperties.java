package com.teamora.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Strongly-typed binding for the {@code teamora.*} config tree. */
@ConfigurationProperties(prefix = "teamora")
public record TeamoraProperties(
        boolean seed,
        Jwt jwt,
        Cors cors
) {
    public record Jwt(
            String secret,
            long accessTokenTtlMinutes,
            long refreshTokenTtlDays
    ) {}

    public record Cors(String allowedOrigins) {}
}
