package com.teamora.leave;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Configurable leave-type catalogue: staff read active types, admins CRUD, RBAC is
 * enforced (401 unauth / 403 forbidden), and a staff member can apply by the new id.
 */
class LeaveTypeConfigIT extends AbstractIntegrationTest {

    private record Co(String owner, String mgr, String emp, String empEmail) {}

    private JsonNode register(String slug) throws Exception {
        String body = """
                {"companyName":"LT %s","fullName":"Owner %s","email":"ltowner-%s@example.com","password":"password"}"""
                .formatted(slug, slug, slug);
        var res = mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(res.getResponse().getContentAsString());
    }

    private void createEmp(String owner, String name, String email, String role) throws Exception {
        String body = "{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"password\",\"role\":\"%s\"}".formatted(name, email, role);
        mvc.perform(post("/api/employees").header("Authorization", bearer(owner))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated());
    }

    private Co setup(String slug) throws Exception {
        String owner = register(slug).get("accessToken").asText();
        createEmp(owner, "Mgr " + slug, "ltmgr-" + slug + "@example.com", "MANAGER");
        String empEmail = "ltemp-" + slug + "@example.com";
        createEmp(owner, "Emp " + slug, empEmail, "EMPLOYEE");
        return new Co(owner,
                login("ltmgr-" + slug + "@example.com", "password"),
                login(empEmail, "password"),
                empEmail);
    }

    @Test
    void staffSeesSeededActiveTypes() throws Exception {
        Co co = setup("s" + System.nanoTime());
        mvc.perform(get("/api/leave/types").header("Authorization", bearer(co.emp())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[?(@.code=='ANNUAL')].paid").value(org.hamcrest.Matchers.contains(true)))
                .andExpect(jsonPath("$[?(@.code=='UNPAID')].paid").value(org.hamcrest.Matchers.contains(false)));
    }

    @Test
    void leaveTypes_requireAuthentication() throws Exception {
        mvc.perform(get("/api/leave/types")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/leave/types")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/leave/types").contentType("application/json")
                        .content("{\"name\":\"Study Leave\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerCanCreateAndUpdate_managerAndEmployeeCannot() throws Exception {
        Co co = setup("c" + System.nanoTime());
        String create = "{\"name\":\"Study Leave\",\"paid\":false,\"defaultEntitlementDays\":3,\"colorKey\":\"violet\"}";

        // Employee (not even in admin URL space) → 403.
        mvc.perform(post("/api/admin/leave/types").header("Authorization", bearer(co.emp()))
                        .contentType("application/json").content(create))
                .andExpect(status().isForbidden());
        // Manager is allowed on /api/admin/** by URL, but config-mutating needs OWNER/HR_ADMIN → 403.
        mvc.perform(post("/api/admin/leave/types").header("Authorization", bearer(co.mgr()))
                        .contentType("application/json").content(create))
                .andExpect(status().isForbidden());

        // Owner creates it.
        var res = mvc.perform(post("/api/admin/leave/types").header("Authorization", bearer(co.owner()))
                        .contentType("application/json").content(create))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("STUDY_LEAVE"))
                .andExpect(jsonPath("$.paid").value(false))
                .andReturn();
        String id = om.readTree(res.getResponse().getContentAsString()).get("id").asText();

        // Owner updates it; manager cannot.
        mvc.perform(patch("/api/admin/leave/types/" + id).header("Authorization", bearer(co.mgr()))
                        .contentType("application/json").content("{\"defaultEntitlementDays\":5}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/leave/types/" + id).header("Authorization", bearer(co.owner()))
                        .contentType("application/json").content("{\"defaultEntitlementDays\":5,\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.defaultEntitlementDays").value(5))
                .andExpect(jsonPath("$.active").value(false));

        // Deactivated type drops out of the staff picker but stays in the admin catalogue.
        mvc.perform(get("/api/leave/types").header("Authorization", bearer(co.emp())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='STUDY_LEAVE')]").isEmpty());
        mvc.perform(get("/api/admin/leave/types").header("Authorization", bearer(co.mgr())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.code=='STUDY_LEAVE')]").isNotEmpty());
    }

    @Test
    void create_rejectsBlankName() throws Exception {
        Co co = setup("v" + System.nanoTime());
        mvc.perform(post("/api/admin/leave/types").header("Authorization", bearer(co.owner()))
                        .contentType("application/json").content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCatalogue_forbiddenForEmployee() throws Exception {
        Co co = setup("a" + System.nanoTime());
        mvc.perform(get("/api/admin/leave/types").header("Authorization", bearer(co.emp())))
                .andExpect(status().isForbidden());
    }

    @Test
    void staffCanApplyWithNewLeaveTypeId() throws Exception {
        Co co = setup("p" + System.nanoTime());
        String annual = leaveTypeId(co.emp(), "ANNUAL");
        mvc.perform(post("/api/leave/requests").header("Authorization", bearer(co.emp()))
                        .contentType("application/json")
                        .content("{\"leaveTypeId\":\"%s\",\"startDate\":\"2026-05-04\",\"endDate\":\"2026-05-05\",\"reason\":\"x\"}".formatted(annual)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.typeCode").value("ANNUAL"))
                .andExpect(jsonPath("$.status").value("PENDING"));
    }
}
