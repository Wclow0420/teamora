package com.teamora.auth;

import com.teamora.employee.Employee;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

/**
 * Default sender: writes the code to the server log (no email provider yet).
 * A real sender replaces it by selecting another value for
 * {@code teamora.password-reset.sender} with its own {@code @ConditionalOnProperty}.
 */
@Slf4j
@Component
@ConditionalOnProperty(name = "teamora.password-reset.sender", havingValue = "log", matchIfMissing = true)
public class LoggingPasswordResetSender implements PasswordResetSender {

    @Override
    public void send(Employee employee, String code) {
        log.info("Password reset code for {}: {}", employee.getEmail(), code);
    }
}
