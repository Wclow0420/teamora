package com.teamora.approval;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.endsWith;
import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Declining with an optional reason (leave / claims / overtime): the reason is trimmed and
 * stored as {@code decisionNote}, shown on the requester's own list and appended to the
 * "declined" notification; no body keeps working; over 300 chars is a 400.
 */
class DeclineReasonIT extends AbstractIntegrationTest {

    private record Co(String owner, String emp) {}

    /** A fresh company with one EMPLOYEE (no reporting manager → the owner approves). */
    private Co setup(String slug) throws Exception {
        String reg = """
                {"companyName":"DEC %s","fullName":"Owner %s","email":"decowner-%s@example.com","password":"password"}"""
                .formatted(slug, slug, slug);
        var res = mvc.perform(post("/api/auth/register").contentType("application/json").content(reg))
                .andExpect(status().isOk()).andReturn();
        String owner = om.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
        mvc.perform(post("/api/employees").header("Authorization", bearer(owner)).contentType("application/json")
                        .content("{\"fullName\":\"Dec Emp\",\"email\":\"decemp-%s@example.com\",\"password\":\"password\",\"role\":\"EMPLOYEE\"}"
                                .formatted(slug)))
                .andExpect(status().isCreated());
        return new Co(owner, login("decemp-" + slug + "@example.com", "password"));
    }

    private String id(org.springframework.test.web.servlet.ResultActions r) throws Exception {
        return om.readTree(r.andReturn().getResponse().getContentAsString()).get("id").asText();
    }

    private String applyLeave(String emp, String annual, String date) throws Exception {
        return id(mvc.perform(post("/api/leave/requests").header("Authorization", bearer(emp))
                        .contentType("application/json")
                        .content("{\"leaveTypeId\":\"%s\",\"startDate\":\"%s\",\"endDate\":\"%s\",\"reason\":\"trip\"}"
                                .formatted(annual, date, date)))
                .andExpect(status().isOk()));
    }

    private JsonNode myLeave(String emp, String leaveId) throws Exception {
        var res = mvc.perform(get("/api/leave/requests").header("Authorization", bearer(emp)))
                .andExpect(status().isOk()).andReturn();
        for (JsonNode n : om.readTree(res.getResponse().getContentAsString())) {
            if (n.get("id").asText().equals(leaveId)) return n;
        }
        throw new AssertionError("leave request not in list: " + leaveId);
    }

    private String latestNotificationBody(String token) throws Exception {
        var res = mvc.perform(get("/api/notifications").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(res.getResponse().getContentAsString(java.nio.charset.StandardCharsets.UTF_8)).get("today").get(0).get("body").asText();
    }

    @Test
    void leave_rejectWithReason_storesTrimmedNote_listsIt_andNotifies() throws Exception {
        Co co = setup("l" + System.nanoTime());
        String annual = leaveTypeId(co.emp(), "ANNUAL");

        // Over 300 chars → 400, and the request is still pending (decidable afterwards).
        String withReason = applyLeave(co.emp(), annual, "2026-12-08");
        mvc.perform(post("/api/admin/leave/requests/" + withReason + "/reject").header("Authorization", bearer(co.owner()))
                        .contentType("application/json").content("{\"reason\":\"" + "x".repeat(301) + "\"}"))
                .andExpect(status().isBadRequest());
        org.assertj.core.api.Assertions.assertThat(myLeave(co.emp(), withReason).get("status").asText())
                .isEqualTo("PENDING");

        mvc.perform(post("/api/admin/leave/requests/" + withReason + "/reject").header("Authorization", bearer(co.owner()))
                        .contentType("application/json").content("{\"reason\":\"  Short-staffed that week  \"}"))
                .andExpect(status().isOk());

        JsonNode row = myLeave(co.emp(), withReason);
        org.assertj.core.api.Assertions.assertThat(row.get("status").asText()).isEqualTo("REJECTED");
        org.assertj.core.api.Assertions.assertThat(row.get("decisionNote").asText()).isEqualTo("Short-staffed that week");
        org.assertj.core.api.Assertions.assertThat(latestNotificationBody(co.emp()))
                .endsWith("was declined: Short-staffed that week");

        // No body at all still works (backwards compatible) → null note, plain notification.
        String noBody = applyLeave(co.emp(), annual, "2026-12-09");
        mvc.perform(post("/api/admin/leave/requests/" + noBody + "/reject").header("Authorization", bearer(co.owner())))
                .andExpect(status().isOk());
        JsonNode plain = myLeave(co.emp(), noBody);
        org.assertj.core.api.Assertions.assertThat(plain.get("status").asText()).isEqualTo("REJECTED");
        org.assertj.core.api.Assertions.assertThat(plain.has("decisionNote")).isTrue();
        org.assertj.core.api.Assertions.assertThat(plain.get("decisionNote").isNull()).isTrue();
        org.assertj.core.api.Assertions.assertThat(latestNotificationBody(co.emp())).endsWith("was declined.");

        // A blank reason is treated as no reason; approving never sets a note.
        String blank = applyLeave(co.emp(), annual, "2026-12-10");
        mvc.perform(post("/api/admin/leave/requests/" + blank + "/reject").header("Authorization", bearer(co.owner()))
                        .contentType("application/json").content("{\"reason\":\"   \"}"))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(myLeave(co.emp(), blank).get("decisionNote").isNull()).isTrue();
        String approved = applyLeave(co.emp(), annual, "2026-12-11");
        mvc.perform(post("/api/admin/leave/requests/" + approved + "/approve").header("Authorization", bearer(co.owner())))
                .andExpect(status().isOk());
        org.assertj.core.api.Assertions.assertThat(myLeave(co.emp(), approved).get("decisionNote").isNull()).isTrue();
    }

    @Test
    void claim_rejectWithReason_andWithoutBody() throws Exception {
        Co co = setup("c" + System.nanoTime());
        String claim = """
                {"category":"TRAVEL","title":"%s","amount":42.50,"claimDate":"2026-09-30"}""";

        String withReason = id(mvc.perform(post("/api/claims").header("Authorization", bearer(co.emp()))
                .contentType("application/json").content(claim.formatted("Grab to client"))).andExpect(status().isOk()));
        mvc.perform(post("/api/admin/claims/" + withReason + "/reject").header("Authorization", bearer(co.owner()))
                        .contentType("application/json").content("{\"reason\":\"" + "y".repeat(301) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/claims/" + withReason + "/reject").header("Authorization", bearer(co.owner()))
                        .contentType("application/json").content("{\"reason\":\"Please attach the receipt\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("REJECTED"))
                .andExpect(jsonPath("$.decisionNote").value("Please attach the receipt"));
        mvc.perform(get("/api/claims").header("Authorization", bearer(co.emp())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claims[?(@.id=='" + withReason + "')].decisionNote").value(
                        org.hamcrest.Matchers.contains("Please attach the receipt")));
        org.assertj.core.api.Assertions.assertThat(latestNotificationBody(co.emp()))
                .isEqualTo("Your claim 'Grab to client' (RM 42.50) was declined: Please attach the receipt");

        String noBody = id(mvc.perform(post("/api/claims").header("Authorization", bearer(co.emp()))
                .contentType("application/json").content(claim.formatted("Parking"))).andExpect(status().isOk()));
        mvc.perform(post("/api/admin/claims/" + noBody + "/reject").header("Authorization", bearer(co.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionNote").value(nullValue()));
        org.assertj.core.api.Assertions.assertThat(latestNotificationBody(co.emp())).endsWith("was declined.");

        // A long claim title + a max-length reason can't overflow the notification body.
        String longTitle = "T".repeat(250);
        String longClaim = id(mvc.perform(post("/api/claims").header("Authorization", bearer(co.emp()))
                .contentType("application/json").content(claim.formatted(longTitle))).andExpect(status().isOk()));
        mvc.perform(post("/api/admin/claims/" + longClaim + "/reject").header("Authorization", bearer(co.owner()))
                        .contentType("application/json").content("{\"reason\":\"" + "z".repeat(300) + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.decisionNote").value("z".repeat(300)));
        org.assertj.core.api.Assertions.assertThat(latestNotificationBody(co.emp())).hasSize(500).endsWith("…");
    }

    @Test
    void overtime_rejectWithReason_andWithoutBody() throws Exception {
        Co co = setup("o" + System.nanoTime());
        String ot = "{\"workDate\":\"%s\",\"hours\":2,\"reason\":\"late shift\"}";

        String withReason = id(mvc.perform(post("/api/overtime").header("Authorization", bearer(co.emp()))
                .contentType("application/json").content(ot.formatted("2026-09-28"))).andExpect(status().isOk()));
        mvc.perform(post("/api/admin/overtime/" + withReason + "/reject").header("Authorization", bearer(co.owner()))
                        .contentType("application/json").content("{\"reason\":\"" + "o".repeat(301) + "\"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/overtime/" + withReason + "/reject").header("Authorization", bearer(co.owner()))
                        .contentType("application/json").content("{\"reason\":\"Not pre-approved\"}"))
                .andExpect(status().isOk());
        mvc.perform(get("/api/overtime").header("Authorization", bearer(co.emp())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + withReason + "')].decisionNote").value(
                        org.hamcrest.Matchers.contains("Not pre-approved")))
                .andExpect(jsonPath("$[?(@.id=='" + withReason + "')].status").value(
                        org.hamcrest.Matchers.contains("REJECTED")));
        org.assertj.core.api.Assertions.assertThat(latestNotificationBody(co.emp()))
                .isEqualTo("Your 2h overtime was declined: Not pre-approved");

        String noBody = id(mvc.perform(post("/api/overtime").header("Authorization", bearer(co.emp()))
                .contentType("application/json").content(ot.formatted("2026-09-29"))).andExpect(status().isOk()));
        mvc.perform(post("/api/admin/overtime/" + noBody + "/reject").header("Authorization", bearer(co.owner())))
                .andExpect(status().isOk());
        mvc.perform(get("/api/overtime").header("Authorization", bearer(co.emp())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + noBody + "')].decisionNote").value(
                        org.hamcrest.Matchers.contains(nullValue())));
        org.assertj.core.api.Assertions.assertThat(latestNotificationBody(co.emp()))
                .isEqualTo("Your 2h overtime was declined.");
    }
}
