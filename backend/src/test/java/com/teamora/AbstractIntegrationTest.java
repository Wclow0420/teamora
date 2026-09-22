package com.teamora;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.JsonNode;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Base for integration tests: boots the full app + security chain against the
 * Postgres given by SPRING_DATASOURCE_* (the test runner points this at a
 * disposable `teamora_test` database — see scripts/test-backend.sh). Flyway
 * migrations + JPA validation run, and seeding is on, so the demo accounts
 * (amir@lumi.com, sarah@lumi.com, …) exist.
 */
@SpringBootTest
@AutoConfigureMockMvc
public abstract class AbstractIntegrationTest {

    @Autowired
    protected MockMvc mvc;

    @Autowired
    protected ObjectMapper om;

    /** Log in and return the access token (asserts 200). */
    protected String login(String email, String password) throws Exception {
        String body = """
                {"email":"%s","password":"%s"}""".formatted(email, password);
        var res = mvc.perform(post("/api/auth/login").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andReturn();
        return om.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    protected String bearer(String token) {
        return "Bearer " + token;
    }

    /** Resolve a leave-type id by its stable code for the token's company (asserts 200 + a match). */
    protected String leaveTypeId(String token, String code) throws Exception {
        var res = mvc.perform(get("/api/leave/types").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andReturn();
        JsonNode types = om.readTree(res.getResponse().getContentAsString());
        for (JsonNode t : types) {
            if (code.equals(t.get("code").asText())) {
                return t.get("id").asText();
            }
        }
        throw new AssertionError("No leave type with code " + code);
    }
}
