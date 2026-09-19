package com.teamora.notification;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Auto-notifications on a decision + push-token registration. */
class NotificationFlowIT extends AbstractIntegrationTest {

    private JsonNode register(String slug) throws Exception {
        String body = """
                {"companyName":"Notif %s","fullName":"Owner %s","email":"notifowner-%s@example.com","password":"password"}"""
                .formatted(slug, slug, slug);
        var res = mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(res.getResponse().getContentAsString());
    }

    @Test
    void approvingLeave_notifiesTheRequester() throws Exception {
        String slug = "n" + System.nanoTime();
        JsonNode reg = register(slug);
        String ownerToken = reg.get("accessToken").asText();
        // employee with no manager → owner is the approver
        mvc.perform(post("/api/employees").header("Authorization", bearer(ownerToken)).contentType("application/json")
                        .content("{\"fullName\":\"Emp One\",\"email\":\"nemp-%s@example.com\",\"password\":\"password\",\"role\":\"EMPLOYEE\"}".formatted(slug)))
                .andExpect(status().isCreated());
        String empToken = login("nemp-" + slug + "@example.com", "password");

        var applied = mvc.perform(post("/api/leave/requests").header("Authorization", bearer(empToken)).contentType("application/json")
                        .content("{\"leaveType\":\"ANNUAL\",\"startDate\":\"2026-09-10\",\"endDate\":\"2026-09-11\",\"reason\":\"x\"}"))
                .andExpect(status().isOk()).andReturn();
        String leaveId = om.readTree(applied.getResponse().getContentAsString()).get("id").asText();

        // owner approves → requester should get a notification
        mvc.perform(post("/api/admin/leave/requests/" + leaveId + "/approve").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());

        mvc.perform(get("/api/notifications").header("Authorization", bearer(empToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.today[0].title").value("Leave approved"));
    }

    @Test
    void pushToken_registersAndIsIdempotent() throws Exception {
        String token = login("amir@lumi.com", "password");
        String body = "{\"token\":\"ExponentPushToken[abc-" + System.nanoTime() + "]\",\"platform\":\"ios\"}";
        mvc.perform(post("/api/notifications/push-token").header("Authorization", bearer(token))
                        .contentType("application/json").content(body))
                .andExpect(status().isNoContent());
        // re-registering the same token is fine (upsert)
        mvc.perform(post("/api/notifications/push-token").header("Authorization", bearer(token))
                        .contentType("application/json").content(body))
                .andExpect(status().isNoContent());
    }
}
