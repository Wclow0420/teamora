package com.teamora.leave;

import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** End-to-end leave flow: an employee applies, an admin approves. */
class LeaveFlowIT extends AbstractIntegrationTest {

    @Test
    void employeeAppliesForLeave_andItShowsUpPending() throws Exception {
        String token = login("amir@lumi.com", "password");
        String annual = leaveTypeId(token, "ANNUAL");
        String body = """
                {"leaveTypeId":"%s","startDate":"2026-08-10","endDate":"2026-08-12","reason":"Trip"}""".formatted(annual);

        mvc.perform(post("/api/leave/requests").header("Authorization", bearer(token))
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.durationLabel").value("3 days"));
    }

    @Test
    void adminSeesPending_andCanApprove() throws Exception {
        // Amir applies.
        String amir = login("amir@lumi.com", "password");
        String annual = leaveTypeId(amir, "ANNUAL");
        mvc.perform(post("/api/leave/requests").header("Authorization", bearer(amir))
                        .contentType("application/json")
                        .content("{\"leaveTypeId\":\"%s\",\"startDate\":\"2026-09-01\",\"endDate\":\"2026-09-01\",\"reason\":\"Errand\"}".formatted(annual)))
                .andExpect(status().isOk());

        // Admin sees pending requests for the company and approves the first one.
        String sarah = login("sarah@lumi.com", "password");
        var res = mvc.perform(get("/api/admin/leave/requests").param("status", "PENDING")
                        .header("Authorization", bearer(sarah)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andReturn();
        String id = om.readTree(res.getResponse().getContentAsString()).get(0).get("id").asText();

        mvc.perform(post("/api/admin/leave/requests/" + id + "/approve")
                        .header("Authorization", bearer(sarah)))
                .andExpect(status().isOk());
    }

    @Test
    void employeeCannotApprove() throws Exception {
        String amir = login("amir@lumi.com", "password");
        mvc.perform(post("/api/admin/leave/requests/" + java.util.UUID.randomUUID() + "/approve")
                        .header("Authorization", bearer(amir)))
                .andExpect(status().isForbidden());
    }
}
