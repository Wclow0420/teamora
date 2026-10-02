package com.teamora.auth;

import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.TestPropertySource;

import java.util.UUID;

import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * With {@code teamora.password-reset.expose-code} off (the production default),
 * the forgot-password response never carries the code — even for a real account
 * that did get one.
 */
@TestPropertySource(properties = "teamora.password-reset.expose-code=false")
class PasswordResetCodeHiddenIT extends AbstractIntegrationTest {

    @Autowired
    private PasswordResetCodeRepository codes;

    @Test
    void exposeCodeOff_devCodeIsNull_butACodeWasCreated() throws Exception {
        String slug = "hid" + System.nanoTime();
        String email = "reset-" + slug + "@example.com";
        var res = mvc.perform(post("/api/auth/register").contentType("application/json").content("""
                        {"companyName":"Hidden %s","fullName":"Owner %s","email":"%s","password":"password"}"""
                        .formatted(slug, slug, email)))
                .andExpect(status().isOk()).andReturn();
        UUID id = UUID.fromString(om.readTree(res.getResponse().getContentAsString()).get("employee").get("id").asText());

        mvc.perform(post("/api/auth/forgot-password").contentType("application/json")
                        .content("{\"email\":\"%s\"}".formatted(email)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.devCode").value(nullValue()));

        assertTrue(codes.findFirstByEmployeeIdAndUsedAtIsNullOrderByCreatedAtDesc(id).isPresent());
    }
}
