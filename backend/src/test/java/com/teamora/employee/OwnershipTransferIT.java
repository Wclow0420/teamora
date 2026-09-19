package com.teamora.employee;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Single-owner enforcement + ownership transfer. Each test registers its OWN
 * throwaway company so it never mutates the shared seed.
 */
class OwnershipTransferIT extends AbstractIntegrationTest {

    /** Register a new company; returns {token, ownerId}. */
    private JsonNode register(String slug) throws Exception {
        String body = """
                {"companyName":"Co %s","fullName":"Owner %s","email":"%s@example.com","password":"password"}"""
                .formatted(slug, slug, slug);
        var res = mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andReturn();
        return om.readTree(res.getResponse().getContentAsString());
    }

    private String createEmployee(String token, String name, String email, String role) throws Exception {
        String body = """
                {"fullName":"%s","email":"%s","password":"password","role":"%s"}""".formatted(name, email, role);
        var res = mvc.perform(post("/api/employees").header("Authorization", bearer(token))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return om.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void transfer_swapsRoles() throws Exception {
        long n = System.nanoTime();
        JsonNode reg = register("xfer" + n);
        String ownerToken = reg.get("accessToken").asText();
        String ownerId = reg.get("employee").get("id").asText();
        String targetId = createEmployee(ownerToken, "New Boss", "newboss" + n + "@example.com", "HR_ADMIN");

        mvc.perform(post("/api/employees/" + targetId + "/transfer-ownership").header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("OWNER"));

        // The previous owner is now HR_ADMIN (token still valid for the read).
        mvc.perform(get("/api/employees/" + ownerId).header("Authorization", bearer(ownerToken)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("HR_ADMIN"));
    }

    @Test
    void nonOwner_cannotTransfer_403() throws Exception {
        long n = System.nanoTime();
        JsonNode reg = register("noxfer" + n);
        String ownerToken = reg.get("accessToken").asText();
        String hrId = createEmployee(ownerToken, "HR Person", "hr" + n + "@example.com", "HR_ADMIN");
        String empId = createEmployee(ownerToken, "Some Emp", "emp" + n + "@example.com", "EMPLOYEE");

        String hrToken = login("hr" + n + "@example.com", "password");
        mvc.perform(post("/api/employees/" + empId + "/transfer-ownership").header("Authorization", bearer(hrToken)))
                .andExpect(status().isForbidden());
    }

    @Test
    void cannotCreateSecondOwner_viaCreateUpdateOrChangeRole() throws Exception {
        long n = System.nanoTime();
        JsonNode reg = register("twoowner" + n);
        String ownerToken = reg.get("accessToken").asText();

        // create with role OWNER → 400
        mvc.perform(post("/api/employees").header("Authorization", bearer(ownerToken)).contentType("application/json")
                        .content("""
                                {"fullName":"X","email":"x%s@example.com","password":"password","role":"OWNER"}""".formatted(n)))
                .andExpect(status().isBadRequest());

        String empId = createEmployee(ownerToken, "Y Person", "y" + n + "@example.com", "EMPLOYEE");
        // PATCH update role → OWNER → 400
        mvc.perform(patch("/api/employees/" + empId).header("Authorization", bearer(ownerToken))
                        .contentType("application/json").content("{\"role\":\"OWNER\"}"))
                .andExpect(status().isBadRequest());
        // PATCH /role → OWNER → 400
        mvc.perform(patch("/api/employees/" + empId + "/role").header("Authorization", bearer(ownerToken))
                        .contentType("application/json").content("{\"role\":\"OWNER\"}"))
                .andExpect(status().isBadRequest());
    }
}
