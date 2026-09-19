package com.teamora.auth;

import com.teamora.auth.dto.AuthDtos.AuthResponse;
import com.teamora.auth.dto.AuthDtos.LoginRequest;
import com.teamora.auth.dto.AuthDtos.LogoutRequest;
import com.teamora.auth.dto.AuthDtos.RefreshRequest;
import com.teamora.auth.dto.AuthDtos.RegisterRequest;
import com.teamora.employee.dto.EmployeeResponse;
import com.teamora.security.CurrentEmployeeService;
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

    @PostMapping("/login")
    public AuthResponse login(@Valid @RequestBody LoginRequest req) {
        return authService.login(req.email(), req.password());
    }

    /** Self-serve company signup → creates the company + OWNER and signs in. */
    @PostMapping("/register")
    public AuthResponse register(@Valid @RequestBody RegisterRequest req) {
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

    /** Who am I — the authenticated employee. */
    @GetMapping("/me")
    public EmployeeResponse me() {
        return EmployeeResponse.from(currentEmployee.require());
    }
}
