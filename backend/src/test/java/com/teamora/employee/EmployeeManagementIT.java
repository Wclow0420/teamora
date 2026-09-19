package com.teamora.employee;

import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Employee profile/role editing. Creates its own throwaway employees rather than
 * mutating the shared seeded accounts, so it can't leak state into other test
 * classes (they share one test database).
 */
class EmployeeManagementIT extends AbstractIntegrationTest {

    /** Create a fresh EMPLOYEE in the admin's company and return its id. */
    private String createEmployee(String adminToken, String name, String email) throws Exception {
        String body = """
                {"fullName":"%s","email":"%s","password":"password","role":"EMPLOYEE"}""".formatted(name, email);
        var res = mvc.perform(post("/api/employees").header("Authorization", bearer(adminToken))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated())
                .andReturn();
        return om.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private String idByName(String adminToken, String q) throws Exception {
        var res = mvc.perform(get("/api/employees").param("q", q).header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk())
                .andReturn();
        return om.readTree(res.getResponse().getContentAsString()).get(0).get("id").asText();
    }

    @Test
    void admin_updatesNameTitleDepartmentAndRole() throws Exception {
        String sarah = login("sarah@lumi.com", "password");
        String id = createEmployee(sarah, "Edit Target One", "edit-target-1@lumi.com");

        mvc.perform(patch("/api/employees/" + id).header("Authorization", bearer(sarah))
                        .contentType("application/json")
                        .content("""
                                {"fullName":"Edited Name","jobTitle":"Senior Exec","department":"Sales","role":"MANAGER"}"""))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.fullName").value("Edited Name"))
                .andExpect(jsonPath("$.jobTitle").value("Senior Exec"))
                .andExpect(jsonPath("$.department").value("Sales"))
                .andExpect(jsonPath("$.role").value("MANAGER"));
    }

    @Test
    void admin_cannotChangeOwnRole() throws Exception {
        String sarah = login("sarah@lumi.com", "password");
        String sarahId = idByName(sarah, "Sarah");
        mvc.perform(patch("/api/employees/" + sarahId).header("Authorization", bearer(sarah))
                        .contentType("application/json").content("{\"role\":\"EMPLOYEE\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cannotUpdateAnotherTenantsEmployee() throws Exception {
        String sarah = login("sarah@lumi.com", "password");
        String id = createEmployee(sarah, "Edit Target Two", "edit-target-2@lumi.com");

        String nusantara = login("admin@nusantara.com", "password");
        mvc.perform(patch("/api/employees/" + id).header("Authorization", bearer(nusantara))
                        .contentType("application/json").content("{\"jobTitle\":\"Hacked\"}"))
                .andExpect(status().isNotFound());
    }

    @Test
    void employeeCannotUpdate() throws Exception {
        String amir = login("amir@lumi.com", "password");
        mvc.perform(patch("/api/employees/" + java.util.UUID.randomUUID()).header("Authorization", bearer(amir))
                        .contentType("application/json").content("{\"jobTitle\":\"x\"}"))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_withReportingManager_returnedInResponse() throws Exception {
        String sarah = login("sarah@lumi.com", "password");
        String nadiaId = idByName(sarah, "Nadia");
        var res = mvc.perform(post("/api/employees").header("Authorization", bearer(sarah))
                        .contentType("application/json")
                        .content("""
                                {"fullName":"Reports To Nadia","email":"reports-nadia@lumi.com","password":"password","role":"EMPLOYEE","reportingManagerId":"%s"}"""
                                .formatted(nadiaId)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.reportingManagerId").value(nadiaId))
                .andExpect(jsonPath("$.reportingManagerName").value("Nadia Rahman"))
                .andReturn();
        // and it persists on a subsequent read
        String id = om.readTree(res.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(get("/api/employees/" + id).header("Authorization", bearer(sarah)))
                .andExpect(jsonPath("$.reportingManagerName").value("Nadia Rahman"));
    }

    @Test
    void reportingManager_mustBeManagerial_400() throws Exception {
        String sarah = login("sarah@lumi.com", "password");
        String amirId = idByName(sarah, "Amir");          // an EMPLOYEE
        String targetId = createEmployee(sarah, "Bad Report", "bad-report@lumi.com");
        mvc.perform(patch("/api/employees/" + targetId).header("Authorization", bearer(sarah))
                        .contentType("application/json").content("{\"reportingManagerId\":\"" + amirId + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void managersEndpoint_excludesEmployees() throws Exception {
        String sarah = login("sarah@lumi.com", "password");
        var res = mvc.perform(get("/api/employees/managers").header("Authorization", bearer(sarah)))
                .andExpect(status().isOk())
                .andReturn();
        var arr = om.readTree(res.getResponse().getContentAsString());
        for (var node : arr) {
            String role = node.get("role").asText();
            org.junit.jupiter.api.Assertions.assertNotEquals("EMPLOYEE", role);
        }
    }
}
