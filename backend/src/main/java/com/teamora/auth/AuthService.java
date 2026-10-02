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
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

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

    /** Self-serve signup: create the company and its OWNER, then sign them in. */
    @Transactional
    public AuthResponse register(RegisterRequest req) {
        if (employees.existsByEmailIgnoreCase(req.email())) {
            throw new BadRequestException("An account with this email already exists");
        }
        Company company = companyService.create(req.companyName());
        // Every new tenant gets the default payroll settings + leave catalogue.
        companySettingsService.createDefaults(company);
        leaveTypeService.seedDefaults(company);
        Employee owner = Employee.builder()
                .email(req.email().trim())
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

    /** Rotate a refresh token: revoke the old one and issue a fresh pair. */
    @Transactional
    public AuthResponse refresh(String refreshToken) {
        RefreshToken existing = refreshTokens.findByToken(refreshToken)
                .orElseThrow(() -> new BadRequestException("Invalid refresh token"));
        if (!existing.isActive()) {
            throw new BadRequestException("Refresh token expired or revoked");
        }
        existing.setRevoked(true);
        return issue(existing.getEmployee());
    }

    @Transactional
    public void logout(String refreshToken) {
        refreshTokens.findByToken(refreshToken).ifPresent(t -> t.setRevoked(true));
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
        refreshTokens.revokeAllForEmployee(employeeId, keepRefreshToken == null ? "" : keepRefreshToken);
    }

    private AuthResponse issue(Employee employee) {
        String accessToken = jwtService.generateAccessToken(employee);
        RefreshToken refresh = RefreshToken.builder()
                .token(UUID.randomUUID().toString())
                .employee(employee)
                .expiresAt(Instant.now().plus(props.jwt().refreshTokenTtlDays(), ChronoUnit.DAYS))
                .revoked(false)
                .build();
        refreshTokens.save(refresh);
        return new AuthResponse(
                accessToken,
                refresh.getToken(),
                "Bearer",
                jwtService.accessTtlSeconds(),
                EmployeeResponse.from(employee));
    }
}
