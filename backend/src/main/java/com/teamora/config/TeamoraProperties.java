package com.teamora.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.time.Duration;

/** Strongly-typed binding for the {@code teamora.*} config tree. */
@ConfigurationProperties(prefix = "teamora")
public record TeamoraProperties(
        /** {@code dev} (default) or {@code production}. */
        String env,
        boolean seed,
        Jwt jwt,
        Cors cors,
        PasswordReset passwordReset,
        RateLimit rateLimit,
        /** Largest accepted request body in bytes (413 above it). */
        Long maxRequestBytes
) {
    public static final String ENV_DEV = "dev";
    public static final String ENV_PRODUCTION = "production";

    /** 3.5 MB — a 2 MB photo base64-encoded inside a JSON body fits comfortably. */
    public static final long DEFAULT_MAX_REQUEST_BYTES = 3_670_016L;

    /** True when {@code TEAMORA_ENV=production}. */
    public boolean isProduction() {
        return env != null && ENV_PRODUCTION.equalsIgnoreCase(env.trim());
    }

    public long maxRequestBytesOrDefault() {
        return maxRequestBytes != null && maxRequestBytes > 0 ? maxRequestBytes : DEFAULT_MAX_REQUEST_BYTES;
    }

    /**
     * {@code enabled}: false → forgot-password creates and sends nothing (still 200).
     * {@code sender}: which {@link com.teamora.auth.PasswordResetSender} is active ("log" by default).
     * {@code exposeCode}: LOCAL DEV / TEST ONLY — return the one-time reset code in
     * the forgot-password response so the flow is testable without an email provider.
     * {@code maxCodesPerDay}: per-account cap on codes per rolling 24h.
     */
    public record PasswordReset(Boolean enabled, String sender, boolean exposeCode, Integer maxCodesPerDay) {
        public boolean isEnabled() {
            return enabled == null || enabled;
        }

        public String senderOrDefault() {
            return sender == null || sender.isBlank() ? "log" : sender.trim();
        }

        public int maxCodesPerDayOrDefault() {
            return maxCodesPerDay != null && maxCodesPerDay > 0 ? maxCodesPerDay : 10;
        }
    }

    /** True only when the reset code may be echoed back to the caller (never in production). */
    public boolean exposeResetCode() {
        return passwordReset != null && passwordReset.exposeCode();
    }

    public PasswordReset passwordResetOrDefault() {
        return passwordReset != null ? passwordReset : new PasswordReset(true, "log", false, 10);
    }

    public record Jwt(
            String secret,
            long accessTokenTtlMinutes,
            long refreshTokenTtlDays,
            Long refreshReuseGraceSeconds
    ) {
        public long refreshReuseGraceSecondsOrDefault() {
            return refreshReuseGraceSeconds != null && refreshReuseGraceSeconds >= 0 ? refreshReuseGraceSeconds : 60;
        }
    }

    public record Cors(String allowedOrigins) {}

    /** At most {@code max} attempts per {@code window} for one key (an email or an IP). */
    public record Limit(int max, Duration window) {}

    /** Brute-force / abuse limits on the public auth endpoints. */
    public record RateLimit(
            Boolean enabled,
            Limit loginPerEmail,
            Limit loginPerIp,
            Limit registerPerIp,
            Limit forgotPasswordPerIp,
            Limit resetPasswordPerIp
    ) {
        public boolean isEnabled() {
            return enabled == null || enabled;
        }
    }

    public RateLimit rateLimitOrDefault() {
        RateLimit r = rateLimit;
        return new RateLimit(
                r == null ? Boolean.TRUE : r.enabled(),
                or(r == null ? null : r.loginPerEmail(), new Limit(10, Duration.ofMinutes(15))),
                or(r == null ? null : r.loginPerIp(), new Limit(30, Duration.ofMinutes(15))),
                or(r == null ? null : r.registerPerIp(), new Limit(5, Duration.ofMinutes(1))),
                or(r == null ? null : r.forgotPasswordPerIp(), new Limit(5, Duration.ofMinutes(1))),
                or(r == null ? null : r.resetPasswordPerIp(), new Limit(10, Duration.ofMinutes(15))));
    }

    private static Limit or(Limit value, Limit fallback) {
        return value != null && value.max() > 0 && value.window() != null && !value.window().isZero() ? value : fallback;
    }
}
