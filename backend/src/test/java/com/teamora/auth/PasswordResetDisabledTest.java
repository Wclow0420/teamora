package com.teamora.auth;

import com.teamora.config.TeamoraProperties;
import com.teamora.employee.EmployeeRepository;
import com.teamora.notification.PushTokenRepository;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

/** PASSWORD_RESET_ENABLED=false: forgot-password is a silent no-op (the endpoint still answers 200). */
class PasswordResetDisabledTest {

    @Test
    void disabled_createsAndSendsNothing() {
        EmployeeRepository employees = mock(EmployeeRepository.class);
        PasswordResetCodeRepository codes = mock(PasswordResetCodeRepository.class);
        PasswordResetSender sender = mock(PasswordResetSender.class);
        TeamoraProperties props = new TeamoraProperties("production", false, null, null,
                new TeamoraProperties.PasswordReset(false, "log", false, 10), null, null);
        PasswordResetService service = new PasswordResetService(employees, codes, mock(RefreshTokenRepository.class),
                mock(PushTokenRepository.class), mock(PasswordEncoder.class), sender, props);

        assertThat(service.requestCode("amir@lumi.com")).isNull();
        verifyNoInteractions(employees, codes, sender);
    }
}
