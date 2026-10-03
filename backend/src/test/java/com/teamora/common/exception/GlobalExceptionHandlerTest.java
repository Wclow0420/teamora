package com.teamora.common.exception;

import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;

/** Unexpected errors never leak internals; constraint violations are a 409. */
class GlobalExceptionHandlerTest {

    private final GlobalExceptionHandler handler = new GlobalExceptionHandler();

    @Test
    void unexpectedError_isGenericWithAReference() {
        var res = handler.handleGeneric(new IllegalStateException("password=hunter2 at row 7"),
                new MockHttpServletRequest("GET", "/api/x"));
        assertThat(res.getStatusCode().value()).isEqualTo(500);
        assertThat(res.getBody().message()).matches("Something went wrong \\(ref [A-Z2-9]{6}\\)");
        assertThat(res.getBody().message()).doesNotContain("hunter2");
    }

    @Test
    void dataIntegrityViolation_is409_withoutSqlDetail() {
        var res = handler.handleIntegrity(
                new DataIntegrityViolationException("duplicate key value violates unique constraint \"uq_x\""),
                new MockHttpServletRequest("POST", "/api/x"));
        assertThat(res.getStatusCode().value()).isEqualTo(409);
        assertThat(res.getBody().message()).doesNotContain("uq_x").doesNotContain("duplicate key");
    }
}
