package com.teamora.dashboard;

import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Admin Dashboard summary: RBAC + shape of GET /api/admin/dashboard. */
class DashboardIT extends AbstractIntegrationTest {

    @Test
    void hrAdminGetsDashboardSummary() throws Exception {
        String sarah = login("sarah@lumi.com", "password");

        var res = mvc.perform(get("/api/admin/dashboard").header("Authorization", bearer(sarah)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headcount").value(greaterThan(0)))
                .andExpect(jsonPath("$.presentToday").exists())
                .andExpect(jsonPath("$.onLeaveToday").exists())
                .andExpect(jsonPath("$.pendingApprovals").exists())
                .andExpect(jsonPath("$.payrollDueLabel").exists())
                .andExpect(jsonPath("$.week.labels.length()").value(7))
                .andExpect(jsonPath("$.week.present.length()").value(7))
                .andExpect(jsonPath("$.week.rateLabel").exists())
                .andExpect(jsonPath("$.activity").isArray())
                .andReturn();

        // week.max == headcount.
        var json = om.readTree(res.getResponse().getContentAsString());
        long headcount = json.get("headcount").asLong();
        long max = json.get("week").get("max").asLong();
        org.junit.jupiter.api.Assertions.assertEquals(headcount, max);
    }

    @Test
    void managerCanAccessDashboard() throws Exception {
        String nadia = login("nadia@lumi.com", "password");
        mvc.perform(get("/api/admin/dashboard").header("Authorization", bearer(nadia)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.headcount").value(greaterThan(0)));
    }

    @Test
    void employeeIsForbidden() throws Exception {
        String amir = login("amir@lumi.com", "password");
        mvc.perform(get("/api/admin/dashboard").header("Authorization", bearer(amir)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticatedIsUnauthorized() throws Exception {
        mvc.perform(get("/api/admin/dashboard"))
                .andExpect(status().isUnauthorized());
    }
}
