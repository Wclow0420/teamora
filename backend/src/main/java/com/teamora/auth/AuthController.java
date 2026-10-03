package com.teamora.auth;

import com.teamora.auth.dto.AuthDtos.AuthResponse;
import com.teamora.auth.dto.AuthDtos.ChangePasswordRequest;
import com.teamora.auth.dto.AuthDtos.ForgotPasswordRequest;
import com.teamora.auth.dto.AuthDtos.ForgotPasswordResponse;
import com.teamora.auth.dto.AuthDtos.LoginRequest;
import com.teamora.auth.dto.AuthDtos.LogoutRequest;
import com.teamora.auth.dto.AuthDtos.RefreshRequest;
import com.teamora.auth.dto.AuthDtos.RegisterRequest;
import com.teamora.auth.dto.AuthDtos.ResetPasswordRequest;
import com.teamora.common.exception.BadRequestException;
import com.teamora.employee.dto.EmployeeResponse;
import com.teamora.security.CurrentEmployeeService;
import com.teamora.security.RateLimiter;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/auth")
@RequiredArgsConstructor
public class AuthController {

    private final AuthService authService;
    private final CurrentEmployeeService currentEmployee;
    private final PasswordResetService passwordReset;
    private final RateLimiter rateLimiter;

    /** Rate-limited per email (10 / 15 min) and per client IP (30 / 15 min) → 429. */
    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req, HttpServletRequest http) {
        rateLimiter.checkLogin(req.email(), http.getRemoteAddr());
        return authService.login(req.email(), req.password());
    }

    /** Self-serve company signup → creates the company + OWNER and signs in. 5 / min per IP. */
    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req, HttpServletRequest http) {
        rateLimiter.checkRegister(http.getRemoteAddr());
        return authService.register(req);
    }

    @PostMapping("/refresh")
    public AuthResponse refresh(@Valid @RequestBody RefreshRequest req) {
        return authService.refresh(req.refreshToken());
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest req) {
        authService.logout(req.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /** Change the signed-in employee's own password (must know the current one). */
    @PostMapping("/change-password")
    public ResponseEntity<Void> changePassword(@Valid @RequestBody ChangePasswordRequest req) {
        authService.changePassword(currentEmployee.require().getId(),
                req.currentPassword(), req.newPassword(), req.refreshToken());
        return ResponseEntity.noContent().build();
    }

    /**
     * Public. Always 200, whether or not the email has an account (no account
     * enumeration). {@code devCode} is only ever non-null in local dev. 5 / min per IP.
     */
    @PostMapping("/forgot-password")
    public ForgotPasswordResponse forgotPassword(@Valid @RequestBody ForgotPasswordRequest req, HttpServletRequest http) {
        rateLimiter.checkForgotPassword(http.getRemoteAddr());
        return new ForgotPasswordResponse(passwordReset.requestCode(req.email()));
    }

    /**
     * Public. Redeem a one-time code: sets the password and signs the account out everywhere.
     * 10 / 15 min per IP (each code also locks after 5 wrong guesses).
     */
    @PostMapping("/reset-password")
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody ResetPasswordRequest req, HttpServletRequest http) {
        rateLimiter.checkResetPassword(http.getRemoteAddr());
        if (!passwordReset.reset(req.email(), req.code(), req.newPassword())) {
            throw new BadRequestException("That code is incorrect or has expired.");
        }
        return ResponseEntity.noContent().build();
    }

    /** Who am I — the authenticated employee. */
    @GetMapping("/me")
    public EmployeeResponse me() {
        return EmployeeResponse.from(currentEmployee.require());
    }
}
