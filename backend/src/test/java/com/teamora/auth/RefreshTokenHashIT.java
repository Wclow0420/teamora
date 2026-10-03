package com.teamora.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.ResultActions;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Refresh tokens are stored hashed; replaying a rotated token signs the account out everywhere. */
class RefreshTokenHashIT extends AbstractIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    private String newAccount() throws Exception {
        String email = "rt-" + System.nanoTime() + "@example.com";
        mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content("{\"companyName\":\"RT Co\",\"fullName\":\"RT Owner\",\"email\":\"%s\",\"password\":\"password\"}"
                                .formatted(email)))
                .andExpect(status().isOk());
        return email;
    }

    private JsonNode loginFull(String email) throws Exception {
        var res = mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"%s\",\"password\":\"password\"}".formatted(email)))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(res.getResponse().getContentAsString());
    }

    private ResultActions refresh(String token) throws Exception {
        return mvc.perform(post("/api/auth/refresh").contentType("application/json")
                .content("{\"refreshToken\":\"%s\"}".formatted(token)));
    }

    private String refreshOk(String token) throws Exception {
        var res = refresh(token).andExpect(status().isOk()).andReturn();
        return om.readTree(res.getResponse().getContentAsString()).get("refreshToken").asText();
    }

    private long count(String sql, Object... args) {
        Long n = jdbc.queryForObject(sql, Long.class, args);
        return n == null ? 0 : n;
    }

    @Test
    void onlyTheHashIsStored() throws Exception {
        String raw = loginFull(newAccount()).get("refreshToken").asText();
        assertThat(count("SELECT count(*) FROM refresh_tokens WHERE token_hash = ?", raw)).isZero();
        assertThat(count("SELECT count(*) FROM refresh_tokens WHERE token_hash = ?", RefreshToken.hash(raw))).isEqualTo(1);
        // Rotation works through the hash lookup.
        assertThat(refreshOk(raw)).isNotEqualTo(raw);
    }

    @Test
    void retryJustAfterRotation_getsAFreshPair_otherSessionsSurvive() throws Exception {
        String email = newAccount();
        String a = loginFull(email).get("refreshToken").asText();
        String otherDevice = loginFull(email).get("refreshToken").asText();
        refreshOk(a);
        // The app lost the response and retries with the same token within the grace window.
        refreshOk(a);
        refreshOk(otherDevice);
    }

    @Test
    void reuseOfARotatedToken_afterTheGraceWindow_revokesEverySession() throws Exception {
        String email = newAccount();
        String stolen = loginFull(email).get("refreshToken").asText();
        String otherDevice = loginFull(email).get("refreshToken").asText();
        String current = refreshOk(stolen);
        // Push the rotation well outside the grace window.
        jdbc.update("UPDATE refresh_tokens SET rotated_at = now() - interval '1 hour' WHERE token_hash = ?",
                RefreshToken.hash(stolen));

        refresh(stolen).andExpect(status().isBadRequest());
        // Every session of that employee is gone — including the legitimate ones.
        refresh(current).andExpect(status().isBadRequest());
        refresh(otherDevice).andExpect(status().isBadRequest());
        // Signing in again works.
        refreshOk(loginFull(email).get("refreshToken").asText());
    }

    @Test
    void loggedOutToken_isRefused_withoutSigningOutOtherDevices() throws Exception {
        String email = newAccount();
        JsonNode session = loginFull(email);
        String a = session.get("refreshToken").asText();
        String b = loginFull(email).get("refreshToken").asText();
        mvc.perform(post("/api/auth/logout").header("Authorization", bearer(session.get("accessToken").asText()))
                        .contentType("application/json")
                        .content("{\"refreshToken\":\"%s\"}".formatted(a)))
                .andExpect(status().isNoContent());
        refresh(a).andExpect(status().isBadRequest());
        refreshOk(b);
    }
}
