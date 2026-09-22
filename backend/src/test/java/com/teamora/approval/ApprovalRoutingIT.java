package com.teamora.approval;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Approval routing by reporting manager. Builds its own isolated company:
 * one MANAGER, employee A reporting to the manager, employee B with no manager
 * (→ owner). Verifies a manager only sees/approves their reports, HR/Owner
 * override, and null-manager requests route to the owner.
 */
class ApprovalRoutingIT extends AbstractIntegrationTest {

    private record Company(String ownerToken, String mgrToken, String aToken, String bToken) {}

    private String token(JsonNode authJson) { return authJson.get("accessToken").asText(); }

    private JsonNode register(String slug) throws Exception {
        String body = """
                {"companyName":"Co %s","fullName":"Owner %s","email":"owner-%s@example.com","password":"password"}"""
                .formatted(slug, slug, slug);
        var res = mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(res.getResponse().getContentAsString());
    }

    private String createEmp(String ownerToken, String name, String email, String role, String reportingManagerId) throws Exception {
        String mgr = reportingManagerId == null ? "" : ",\"reportingManagerId\":\"" + reportingManagerId + "\"";
        String body = "{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"password\",\"role\":\"%s\"%s}".formatted(name, email, role, mgr);
        var res = mvc.perform(post("/api/employees").header("Authorization", bearer(ownerToken))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn();
        return om.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private String applyLeave(String token) throws Exception {
        String annual = leaveTypeId(token, "ANNUAL");
        var res = mvc.perform(post("/api/leave/requests").header("Authorization", bearer(token))
                        .contentType("application/json")
                        .content("{\"leaveTypeId\":\"%s\",\"startDate\":\"2026-09-10\",\"endDate\":\"2026-09-11\",\"reason\":\"x\"}".formatted(annual)))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    /** Build an isolated company with a manager + employees A (→mgr) and B (→owner). */
    private Company setup(String slug) throws Exception {
        JsonNode reg = register(slug);
        String ownerToken = token(reg);
        String mgrId = createEmp(ownerToken, "Mgr " + slug, "mgr-" + slug + "@example.com", "MANAGER", null);
        createEmp(ownerToken, "Emp A", "a-" + slug + "@example.com", "EMPLOYEE", mgrId);
        createEmp(ownerToken, "Emp B", "b-" + slug + "@example.com", "EMPLOYEE", null);
        return new Company(ownerToken,
                login("mgr-" + slug + "@example.com", "password"),
                login("a-" + slug + "@example.com", "password"),
                login("b-" + slug + "@example.com", "password"));
    }

    @Test
    void manager_seesOnlyReports_andApprovesThem() throws Exception {
        String slug = "r" + System.nanoTime();
        Company co = setup(slug);
        applyLeave(co.aToken());                 // routes to manager
        applyLeave(co.bToken());                 // routes to owner

        // Manager sees only A's request.
        mvc.perform(get("/api/admin/leave/requests").header("Authorization", bearer(co.mgrToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].employeeName").value("Emp A"));
    }

    @Test
    void manager_cannotApproveNonReport_butOwnerCan() throws Exception {
        String slug = "r" + System.nanoTime();
        Company co = setup(slug);
        applyLeave(co.aToken());
        String bLeaveId = applyLeave(co.bToken());   // B → owner

        // Manager cannot approve B's request (not their report).
        mvc.perform(post("/api/admin/leave/requests/" + bLeaveId + "/approve").header("Authorization", bearer(co.mgrToken())))
                .andExpect(status().isForbidden());

        // Owner (override + default approver) can.
        mvc.perform(post("/api/admin/leave/requests/" + bLeaveId + "/approve").header("Authorization", bearer(co.ownerToken())))
                .andExpect(status().isOk());
    }

    @Test
    void owner_seesAllPending() throws Exception {
        String slug = "r" + System.nanoTime();
        Company co = setup(slug);
        applyLeave(co.aToken());
        applyLeave(co.bToken());

        mvc.perform(get("/api/admin/leave/requests").header("Authorization", bearer(co.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2));
    }

    @Test
    void manager_approvesOwnReport() throws Exception {
        String slug = "r" + System.nanoTime();
        Company co = setup(slug);
        String aLeaveId = applyLeave(co.aToken());
        mvc.perform(post("/api/admin/leave/requests/" + aLeaveId + "/approve").header("Authorization", bearer(co.mgrToken())))
                .andExpect(status().isOk());
    }
}
