package com.teamora.location;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Work-location (geofence) config: staff read active sites, admins CRUD the full
 * catalogue, RBAC is enforced (401 unauth / 403 forbidden — managers can't mutate
 * config), radius < 50 is rejected, and sites are tenant-scoped.
 */
class WorkLocationConfigIT extends AbstractIntegrationTest {

    private record Co(String owner, String mgr, String emp) {}

    private JsonNode register(String slug) throws Exception {
        String body = """
                {"companyName":"WL %s","fullName":"Owner %s","email":"wlowner-%s@example.com","password":"password"}"""
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
        createEmp(owner, "Mgr " + slug, "wlmgr-" + slug + "@example.com", "MANAGER");
        createEmp(owner, "Emp " + slug, "wlemp-" + slug + "@example.com", "EMPLOYEE");
        return new Co(owner,
                login("wlmgr-" + slug + "@example.com", "password"),
                login("wlemp-" + slug + "@example.com", "password"));
    }

    @Test
    void endpoints_requireAuthentication() throws Exception {
        mvc.perform(get("/api/work-locations")).andExpect(status().isUnauthorized());
        mvc.perform(get("/api/admin/work-locations")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/work-locations").contentType("application/json")
                        .content("{\"name\":\"HQ\",\"latitude\":3.15,\"longitude\":101.70}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void ownerCanCreateAndUpdate_managerAndEmployeeCannot() throws Exception {
        Co co = setup("c" + System.nanoTime());
        String create = "{\"name\":\"Shop A\",\"latitude\":3.150000,\"longitude\":101.700000}";

        // Employee is outside the admin URL space entirely → 403.
        mvc.perform(post("/api/admin/work-locations").header("Authorization", bearer(co.emp()))
                        .contentType("application/json").content(create))
                .andExpect(status().isForbidden());
        // Manager is allowed on /api/admin/** by URL, but config mutation needs OWNER/HR_ADMIN → 403.
        mvc.perform(post("/api/admin/work-locations").header("Authorization", bearer(co.mgr()))
                        .contentType("application/json").content(create))
                .andExpect(status().isForbidden());

        // Owner creates it — radiusM defaults to 100, active defaults to true.
        var res = mvc.perform(post("/api/admin/work-locations").header("Authorization", bearer(co.owner()))
                        .contentType("application/json").content(create))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Shop A"))
                .andExpect(jsonPath("$.radiusM").value(100))
                .andExpect(jsonPath("$.active").value(true))
                .andReturn();
        String id = om.readTree(res.getResponse().getContentAsString()).get("id").asText();

        // Manager cannot update; owner can (deactivate + widen radius).
        mvc.perform(patch("/api/admin/work-locations/" + id).header("Authorization", bearer(co.mgr()))
                        .contentType("application/json").content("{\"radiusM\":250}"))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/work-locations/" + id).header("Authorization", bearer(co.owner()))
                        .contentType("application/json").content("{\"radiusM\":250,\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.radiusM").value(250))
                .andExpect(jsonPath("$.active").value(false));

        // Deactivated site drops out of the staff picker but stays in the admin catalogue.
        mvc.perform(get("/api/work-locations").header("Authorization", bearer(co.emp())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + id + "')]").isEmpty());
        mvc.perform(get("/api/admin/work-locations").header("Authorization", bearer(co.mgr())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + id + "')]").isNotEmpty());
    }

    @Test
    void create_rejectsRadiusBelowFifty() throws Exception {
        Co co = setup("r" + System.nanoTime());
        mvc.perform(post("/api/admin/work-locations").header("Authorization", bearer(co.owner()))
                        .contentType("application/json")
                        .content("{\"name\":\"Too Tight\",\"latitude\":3.15,\"longitude\":101.70,\"radiusM\":30}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void create_rejectsBlankNameAndMissingCoords() throws Exception {
        Co co = setup("v" + System.nanoTime());
        mvc.perform(post("/api/admin/work-locations").header("Authorization", bearer(co.owner()))
                        .contentType("application/json").content("{\"name\":\"\",\"latitude\":3.15,\"longitude\":101.70}"))
                .andExpect(status().isBadRequest());
        mvc.perform(post("/api/admin/work-locations").header("Authorization", bearer(co.owner()))
                        .contentType("application/json").content("{\"name\":\"No Pin\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void adminCatalogue_forbiddenForEmployee() throws Exception {
        Co co = setup("a" + System.nanoTime());
        mvc.perform(get("/api/admin/work-locations").header("Authorization", bearer(co.emp())))
                .andExpect(status().isForbidden());
    }

    @Test
    void sitesAreTenantScoped() throws Exception {
        Co a = setup("t1-" + System.nanoTime());
        mvc.perform(post("/api/admin/work-locations").header("Authorization", bearer(a.owner()))
                        .contentType("application/json")
                        .content("{\"name\":\"A-Only Site\",\"latitude\":3.15,\"longitude\":101.70}"))
                .andExpect(status().isCreated());

        Co b = setup("t2-" + System.nanoTime());
        mvc.perform(get("/api/admin/work-locations").header("Authorization", bearer(b.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.name=='A-Only Site')]").isEmpty());
    }
}
