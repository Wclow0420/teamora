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

/** Issues and validates HS256 access tokens. */
@Service
public class JwtService {

    private final SecretKey key;
    private final long accessTtlMinutes;

    public JwtService(TeamoraProperties props) {
        this.key = Keys.hmacShaKeyFor(props.jwt().secret().getBytes(StandardCharsets.UTF_8));
        this.accessTtlMinutes = props.jwt().accessTokenTtlMinutes();
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
