package com.teamora.overtime;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Overtime submit + routed approval, in an isolated company. */
class OvertimeFlowIT extends AbstractIntegrationTest {

    private JsonNode register(String slug) throws Exception {
        String body = """
                {"companyName":"OT %s","fullName":"Owner %s","email":"otowner-%s@example.com","password":"password"}"""
                .formatted(slug, slug, slug);
        var res = mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(res.getResponse().getContentAsString());
    }

    private String createEmp(String ownerToken, String name, String email, String role, String mgrId) throws Exception {
        String mgr = mgrId == null ? "" : ",\"reportingManagerId\":\"" + mgrId + "\"";
        String body = "{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"password\",\"role\":\"%s\"%s}".formatted(name, email, role, mgr);
        var res = mvc.perform(post("/api/employees").header("Authorization", bearer(ownerToken))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn();
        return om.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private String submitOt(String token) throws Exception {
        var res = mvc.perform(post("/api/overtime").header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"workDate\":\"2026-09-12\",\"hours\":2.5,\"reason\":\"late shift\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("PENDING"))
                .andExpect(jsonPath("$.hoursLabel").value("2.5h"))
                .andReturn();
        return om.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void submit_route_andDecide() throws Exception {
        String slug = "ot" + System.nanoTime();
        JsonNode reg = register(slug);
        String ownerToken = reg.get("accessToken").asText();
        String mgrId = createEmp(ownerToken, "Mgr", "otmgr-" + slug + "@example.com", "MANAGER", null);
        createEmp(ownerToken, "Emp A", "ota-" + slug + "@example.com", "EMPLOYEE", mgrId);
        createEmp(ownerToken, "Emp B", "otb-" + slug + "@example.com", "EMPLOYEE", null);

        String aToken = login("ota-" + slug + "@example.com", "password");
        String bToken = login("otb-" + slug + "@example.com", "password");
        submitOt(aToken);
        String bOt = submitOt(bToken);

        String mgrToken = login("otmgr-" + slug + "@example.com", "password");
        // Manager sees only A's overtime.
        mvc.perform(get("/api/admin/overtime").header("Authorization", bearer(mgrToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employeeName").value("Emp A"));
        // Manager can't decide B's (not their report); owner can.
        mvc.perform(post("/api/admin/overtime/" + bOt + "/approve").header("Authorization", bearer(mgrToken)))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/overtime/" + bOt + "/approve").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk());
    }

    @Test
    void employeeCannotAccessApprovalsQueue() throws Exception {
        String slug = "ot" + System.nanoTime();
        String ownerToken = register(slug).get("accessToken").asText();
        createEmp(ownerToken, "Emp", "otemp-" + slug + "@example.com", "EMPLOYEE", null);
        String empToken = login("otemp-" + slug + "@example.com", "password");
        mvc.perform(get("/api/admin/overtime").header("Authorization", bearer(empToken)))
                .andExpect(status().isForbidden());
    }
}
