package com.teamora.security;

import com.teamora.config.TeamoraProperties;
import com.teamora.employee.Employee;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/** Issues and validates HS256 access tokens. */
@Service
public class JwtService {

    /** HS256 needs a key of at least 256 bits. */
    static final int MIN_SECRET_BYTES = 32;

    /**
     * Secrets that have ever been published (old defaults, the .env.example
     * placeholder, the committed dev value). Anyone can mint tokens with these, so
     * production refuses them; any secret containing a placeholder marker is refused too.
     */
    static final List<String> KNOWN_PUBLIC_SECRETS = List.of(
            "dev-only-secret-change-me-please-0123456789abcdef0123456789abcdef",
            "change-me-in-prod-this-is-a-dev-only-256-bit-secret-key-0123456789",
            "REPLACE_ME_with_output_of__openssl_rand_-base64_48",
            "test-only-jwt-secret-for-the-integration-suite-0123456789");
    private static final List<String> PLACEHOLDER_MARKERS = List.of("change-me", "replace_me", "replace-me", "dev-only", "test-only");

    private final SecretKey key;
    private final long accessTtlMinutes;

    public JwtService(TeamoraProperties props) {
        String secret = props.jwt() == null ? null : props.jwt().secret();
        validateSecret(secret, props.isProduction());
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
        this.accessTtlMinutes = props.jwt().accessTokenTtlMinutes();
    }

    /** Fail fast at startup on a missing, short or (in production) publicly known signing secret. */
    static void validateSecret(String secret, boolean production) {
        if (secret == null || secret.isBlank()) {
            throw new IllegalStateException(
                    "TEAMORA_JWT_SECRET is not set. Generate one with `openssl rand -base64 48` and put it in backend/.env.");
        }
        if (secret.getBytes(StandardCharsets.UTF_8).length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "TEAMORA_JWT_SECRET is too short: it must be at least " + MIN_SECRET_BYTES
                            + " bytes. Generate one with `openssl rand -base64 48`.");
        }
        if (production) {
            String lower = secret.toLowerCase(Locale.ROOT);
            boolean known = KNOWN_PUBLIC_SECRETS.stream().anyMatch(secret::equals)
                    || PLACEHOLDER_MARKERS.stream().anyMatch(lower::contains);
            if (known) {
                throw new IllegalStateException(
                        "TEAMORA_JWT_SECRET is a published placeholder/dev value and TEAMORA_ENV=production. "
                                + "Set a fresh random secret (`openssl rand -base64 48`).");
            }
        }
    }

    public String generateAccessToken(Employee employee) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(employee.getEmail())
                .claim("role", employee.getRole().name())
                .claim("eid", employee.getId().toString())
                .claim("cid", employee.getCompany() != null ? employee.getCompany().getId().toString() : null)
                .claim("name", employee.getFullName())
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(accessTtlMinutes, ChronoUnit.MINUTES)))
                .signWith(key)
                .compact();
    }

    public long accessTtlSeconds() {
        return accessTtlMinutes * 60;
    }

    public String extractSubject(String token) {
        return parse(token).getSubject();
    }

    public boolean isValid(String token) {
        try {
            parse(token);
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    private Claims parse(String token) {
        return Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
