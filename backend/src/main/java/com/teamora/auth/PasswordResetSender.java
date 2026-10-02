package com.teamora.auth;

import com.teamora.employee.Employee;

/**
 * Delivery channel for a password-reset code (email, SMS, …). Exactly one bean
 * is active; {@link LoggingPasswordResetSender} is the default until a real
 * provider is chosen.
 */
public interface PasswordResetSender {
    void send(Employee employee, String code);
}
