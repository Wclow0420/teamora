package com.teamora.security;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/** The JWT secret is checked at startup: required, >= 32 bytes, never a published value in production. */
class JwtSecretValidationTest {

    private static final String GOOD = "q8Jx1vT0b6m2Hk9sW3nR7cYp4LzA5dEeFgUi0oPlKjMn"; // random-looking, 44 chars

    @Test
    void missingOrBlank_fails() {
        assertThatThrownBy(() -> JwtService.validateSecret(null, false)).hasMessageContaining("not set");
        assertThatThrownBy(() -> JwtService.validateSecret("   ", false)).hasMessageContaining("not set");
    }

    @Test
    void tooShort_fails_inEveryEnv() {
        assertThatThrownBy(() -> JwtService.validateSecret("short-secret", false)).hasMessageContaining("at least 32");
        assertThatThrownBy(() -> JwtService.validateSecret("short-secret", true)).hasMessageContaining("at least 32");
    }

    @Test
    void publishedValues_areFineInDev_butRefusedInProduction() {
        for (String known : JwtService.KNOWN_PUBLIC_SECRETS) {
            assertThatCode(() -> JwtService.validateSecret(known, false)).doesNotThrowAnyException();
            assertThatThrownBy(() -> JwtService.validateSecret(known, true)).hasMessageContaining("placeholder");
        }
        assertThatThrownBy(() -> JwtService.validateSecret("my-company-change-me-secret-0123456789abcdef", true))
                .hasMessageContaining("placeholder");
    }

    @Test
    void randomSecret_isAcceptedInProduction() {
        assertThatCode(() -> JwtService.validateSecret(GOOD, true)).doesNotThrowAnyException();
    }
}
