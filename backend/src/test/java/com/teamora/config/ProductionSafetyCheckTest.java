package com.teamora.config;

import com.teamora.auth.LoggingPasswordResetSender;
import com.teamora.auth.PasswordResetSender;
import org.junit.jupiter.api.Test;

import java.time.Duration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** A production instance refuses dev-only settings; dev is left alone. */
class ProductionSafetyCheckTest {

    private static final PasswordResetSender LOG = new LoggingPasswordResetSender();
    private static final PasswordResetSender REAL = (employee, code) -> { };

    private static TeamoraProperties props(String env, boolean seed, boolean resetEnabled, boolean exposeCode) {
        return new TeamoraProperties(env, seed,
                new TeamoraProperties.Jwt("x".repeat(40), 30, 14, 60L),
                new TeamoraProperties.Cors("*"),
                new TeamoraProperties.PasswordReset(resetEnabled, "log", exposeCode, 10),
                new TeamoraProperties.RateLimit(true, null, null, null, null,
                        new TeamoraProperties.Limit(10, Duration.ofMinutes(15))),
                null);
    }

    @Test
    void dev_allowsEverything() {
        assertThat(ProductionSafetyCheck.problems(props("dev", true, true, true), LOG)).isEmpty();
    }

    @Test
    void production_refusesSeed_exposedCode_andLogOnlyResets() {
        var problems = ProductionSafetyCheck.problems(props("production", true, true, true), LOG);
        assertThat(problems).hasSize(3);
        assertThat(String.join(" ", problems))
                .contains("TEAMORA_SEED").contains("PASSWORD_RESET_EXPOSE_CODE").contains("PASSWORD_RESET_ENABLED=false");
        assertThatThrownBy(() -> new ProductionSafetyCheck(props("production", true, false, false), LOG))
                .isInstanceOf(IllegalStateException.class).hasMessageContaining("Refusing to start");
    }

    @Test
    void production_allowsLogSender_whenResetIsDisabled_orARealSender() {
        assertThat(ProductionSafetyCheck.problems(props("production", false, false, false), LOG)).isEmpty();
        assertThat(ProductionSafetyCheck.problems(props("production", false, true, false), REAL)).isEmpty();
    }

    @Test
    void unknownEnv_isRefused() {
        assertThat(ProductionSafetyCheck.problems(props("prod", false, false, false), LOG))
                .singleElement().asString().contains("TEAMORA_ENV");
    }
}
