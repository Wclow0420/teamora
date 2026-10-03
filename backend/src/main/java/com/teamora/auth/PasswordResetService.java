package com.teamora.auth;

import com.teamora.config.TeamoraProperties;
import com.teamora.employee.Employee;
import com.teamora.employee.EmployeeRepository;
import com.teamora.notification.PushTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;

/**
 * Forgot-password by one-time code. Nothing here reveals whether an email has
 * an account: requesting a code always "succeeds", and redeeming fails the same
 * way for an unknown email as for a wrong code.
 */
@Service
@RequiredArgsConstructor
public class PasswordResetService {

    static final Duration CODE_TTL = Duration.ofMinutes(10);
    static final Duration RATE_WINDOW = Duration.ofMinutes(15);
    static final int MAX_REQUESTS_PER_WINDOW = 3;
    static final int MAX_ATTEMPTS = 5;
    static final Duration DAY = Duration.ofHours(24);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final EmployeeRepository employees;
    private final PasswordResetCodeRepository codes;
    private final RefreshTokenRepository refreshTokens;
    private final PushTokenRepository pushTokens;
    private final PasswordEncoder passwordEncoder;
    private final PasswordResetSender sender;
    private final TeamoraProperties props;

    /**
     * Create + send a code for an active account, within the rate limit.
     *
     * <p>With {@code teamora.password-reset.enabled=false} (production before an email
     * provider exists) nothing is created or sent — the caller still gets a plain 200.
     *
     * @return the plain code only when {@code teamora.password-reset.expose-code}
     *         is on (local dev / tests) and a code was actually created; else null.
     */
    @Transactional
    public String requestCode(String email) {
        if (!props.passwordResetOrDefault().isEnabled()) {
            return null;
        }
        String code = "%06d".formatted(RANDOM.nextInt(1_000_000));
        // Hash before the lookup so the response takes as long for an unknown
        // email as for a real one (no account enumeration by timing).
        String hash = passwordEncoder.encode(code);

        Employee employee = email == null ? null
                : employees.findByEmailIgnoreCase(email.trim()).filter(Employee::isActive).orElse(null);
        if (employee == null) {
            return null;
        }
        Instant now = Instant.now();
        if (codes.countByEmployeeIdAndCreatedAtAfter(employee.getId(), now.minus(RATE_WINDOW))
                >= MAX_REQUESTS_PER_WINDOW) {
            return null;
        }
        // Per-account daily cap, so one inbox can't be flooded by spreading requests out.
        if (codes.countByEmployeeIdAndCreatedAtAfter(employee.getId(), now.minus(DAY))
                >= props.passwordResetOrDefault().maxCodesPerDayOrDefault()) {
            return null;
        }
        codes.invalidateOutstanding(employee.getId(), now);
        codes.save(PasswordResetCode.builder()
                .employee(employee)
                .codeHash(hash)
                .expiresAt(now.plus(CODE_TTL))
                .attempts(0)
                .createdAt(now)
                .build());
        sender.send(employee, code);
        return props.exposeResetCode() ? code : null;
    }

    /**
     * Redeem a code and set the new password. Returns false (never throws) for
     * any failure, so a wrong guess is still committed against the code's
     * attempt counter; the controller turns false into one generic 400.
     */
    @Transactional
    public boolean reset(String email, String code, String newPassword) {
        Employee employee = email == null ? null
                : employees.findByEmailIgnoreCase(email.trim()).filter(Employee::isActive).orElse(null);
        PasswordResetCode current = employee == null ? null
                : codes.findFirstByEmployeeIdAndUsedAtIsNullOrderByCreatedAtDesc(employee.getId()).orElse(null);
        Instant now = Instant.now();
        if (current == null || !current.getExpiresAt().isAfter(now) || current.getAttempts() >= MAX_ATTEMPTS) {
            return false;
        }
        if (code == null || !passwordEncoder.matches(code.trim(), current.getCodeHash())) {
            current.setAttempts(current.getAttempts() + 1);
            return false;
        }
        current.setUsedAt(now);
        employee.setPasswordHash(passwordEncoder.encode(newPassword));
        // Sign the account out everywhere and stop pushing to its old devices.
        refreshTokens.revokeAllForEmployee(employee.getId(), "");
        pushTokens.deleteByEmployeeId(employee.getId());
        return true;
    }
}
