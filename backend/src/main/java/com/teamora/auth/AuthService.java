package com.teamora.auth;

import com.teamora.auth.dto.AuthDtos.AuthResponse;
import com.teamora.auth.dto.AuthDtos.RegisterRequest;
import com.teamora.common.exception.BadRequestException;
import com.teamora.common.exception.ResourceNotFoundException;
import com.teamora.company.Company;
import com.teamora.company.CompanyService;
import com.teamora.company.CompanySettingsService;
import com.teamora.config.TeamoraProperties;
import com.teamora.employee.Employee;
import com.teamora.employee.EmployeeRepository;
import com.teamora.employee.Role;
import com.teamora.leave.LeaveTypeService;
import com.teamora.employee.dto.EmployeeResponse;
import com.teamora.security.JwtService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class AuthService {

    private final AuthenticationManager authenticationManager;
    private final JwtService jwtService;
    private final EmployeeRepository employees;
    private final RefreshTokenRepository refreshTokens;
    private final CompanyService companyService;
    private final CompanySettingsService companySettingsService;
    private final LeaveTypeService leaveTypeService;
    private final PasswordEncoder passwordEncoder;
    private final TeamoraProperties props;

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Emails are stored trimmed + lowercase (V26 enforces uniqueness on lower(email)). */
    public static String normaliseEmail(String email) {
        return email == null ? null : email.trim().toLowerCase(Locale.ROOT);
    }

    /** Self-serve signup: create the company and its OWNER, then sign them in. */
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (employees.existsByEmailIgnoreCase(normaliseEmail(req.email()))) {
            throw new BadRequestException("An account with this email already exists");
        }
        Company company = companyService.create(req.companyName());
        // Every new tenant gets the default payroll settings + leave catalogue.
        companySettingsService.createDefaults(company);
        leaveTypeService.seedDefaults(company);
        Employee owner = Employee.builder()
                .email(normaliseEmail(req.email()))
                .passwordHash(passwordEncoder.encode(req.password()))
                .fullName(req.fullName())
                .role(Role.OWNER)
                .jobTitle("Owner")
                .active(true)
                .build();
        owner.setCompany(company);
        employees.save(owner);
        return issue(owner);
    }

    /** Verify credentials, then issue an access + refresh token pair. */
    @Transactional
    public AuthResponse login(String email, String password) {
        authenticationManager.authenticate(new UsernamePasswordAuthenticationToken(email, password));
        Employee employee = employees.findByEmailIgnoreCase(email)
                .orElseThrow(() -> ResourceNotFoundException.of("Employee", email));
        return issue(employee);
    }

    /**
     * Rotate a refresh token: revoke the old one and issue a fresh pair.
     *
     * <p>Reuse detection: a token that was already exchanged for a new pair being
     * presented again means it was copied — every session of that employee is
     * revoked (they sign in again). The one exception is a replay within the grace
     * window ({@code teamora.jwt.refresh-reuse-grace-seconds}) of its rotation: that's
     * the app retrying after the rotation response was lost, so it gets a fresh pair.
     * Tokens revoked for other reasons (logout, password change) are simply refused.
     */
    // Keep the revocations when refusing (deactivated account / reused token).
    @Transactional(noRollbackFor = {DisabledException.class, BadRequestException.class})
    public AuthResponse refresh(String refreshToken) {
        RefreshToken existing = refreshTokens.findByTokenHash(RefreshToken.hash(refreshToken))
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));
        Employee employee = existing.getEmployee();
        if (!employee.isActive()) {
            // Deactivated: refuse (401, the app signs out) and make sure no session survives.
            refreshTokens.revokeAllForEmployee(employee.getId(), "");
            throw new DisabledException("Account is deactivated");
        }
        if (existing.isExpired()) {
            throw new BadRequestException("Refresh token expired or revoked");
        }
        Instant now = Instant.now();
        if (existing.isRevoked()) {
            if (existing.getRotatedAt() == null) {
                // Revoked by logout / password change / admin reset: that device was signed
                // out on purpose and is just catching up — refuse, but don't punish the
                // sessions that were deliberately kept.
                throw new BadRequestException("Refresh token expired or revoked");
            }
            Duration grace = Duration.ofSeconds(props.jwt().refreshReuseGraceSecondsOrDefault());
            if (existing.getRotatedAt().plus(grace).isAfter(now)) {
                return issue(employee);
            }
            int revoked = refreshTokens.revokeAllForEmployee(employee.getId(), "");
            log.warn("Revoked refresh token reused for employee {} — revoked all {} active session(s)",
                    employee.getId(), revoked);
            throw new BadRequestException("Refresh token expired or revoked");
        }
        existing.setRevoked(true);
        existing.setRotatedAt(now);
        return issue(employee);
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.findByTokenHash(RefreshToken.hash(refreshToken)).ifPresent(t -> t.setRevoked(true));
    }

    /**
     * Change the caller's own password after verifying the current one. Revokes
     * the caller's refresh tokens so other devices are signed out once their
     * access token lapses; {@code keepRefreshToken} (the calling device's own
     * token, optional) is spared so that device stays signed in.
     */
    @Transactional
    public void changePassword(UUID employeeId, String currentPassword, String newPassword, String keepRefreshToken) {
        Employee employee = employees.findById(employeeId)
                .orElseThrow(() -> ResourceNotFoundException.of("Employee", employeeId));
        if (!passwordEncoder.matches(currentPassword, employee.getPasswordHash())) {
            throw new BadRequestException("Current password is incorrect");
        }
        if (passwordEncoder.matches(newPassword, employee.getPasswordHash())) {
            throw new BadRequestException("New password must be different from your current password");
        }
        employee.setPasswordHash(passwordEncoder.encode(newPassword));
        refreshTokens.revokeAllForEmployee(employeeId,
                keepRefreshToken == null || keepRefreshToken.isBlank() ? "" : RefreshToken.hash(keepRefreshToken));
    }

    private AuthResponse issue(Employee employee) {
        String accessToken = jwtService.generateAccessToken(employee);
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        String rawRefresh = Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
        RefreshToken refresh = RefreshToken.builder()
                .tokenHash(RefreshToken.hash(rawRefresh))
                .employee(employee)
                .expiresAt(Instant.now().plus(props.jwt().refreshTokenTtlDays(), ChronoUnit.DAYS))
                .revoked(false)
                .build();
        refreshTokens.save(refresh);
        return new AuthResponse(
                accessToken,
                rawRefresh,
                "Bearer",
                jwtService.accessTtlSeconds(),
                EmployeeResponse.from(employee));
    }
}
