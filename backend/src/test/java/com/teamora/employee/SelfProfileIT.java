package com.teamora.employee;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** PATCH /api/employees/me — self-service phone update; nothing else is self-editable. */
class SelfProfileIT extends AbstractIntegrationTest {

    private String register(String slug) throws Exception {
        String body = """
                {"companyName":"Self %s","fullName":"Owner %s","email":"self-owner-%s@example.com","password":"password"}"""
                .formatted(slug, slug, slug);
        var res = mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String newEmployee(String slug) throws Exception {
        String owner = register(slug);
        String email = "self-emp-" + slug + "@example.com";
        String body = ("{\"fullName\":\"Self Emp\",\"email\":\"%s\",\"password\":\"password\",\"role\":\"EMPLOYEE\","
                + "\"jobTitle\":\"Barista\",\"department\":\"Ops\",\"monthlySalary\":3000}").formatted(email);
        mvc.perform(post("/api/employees").header("Authorization", bearer(owner))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated());
        return login(email, "password");
    }

    private JsonNode me(String token) throws Exception {
        var res = mvc.perform(get("/api/employees/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(res.getResponse().getContentAsString());
    }

    private org.springframework.test.web.servlet.ResultActions patchMe(String token, String body) throws Exception {
        return mvc.perform(patch("/api/employees/me").header("Authorization", bearer(token))
                .contentType("application/json").content(body));
    }

    @Test
    void unauthenticated_401() throws Exception {
        mvc.perform(patch("/api/employees/me").contentType("application/json").content("{\"phone\":\"012\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void employee_updatesTrimsAndClearsOwnPhone() throws Exception {
        String emp = newEmployee("p" + System.nanoTime());

        var res = patchMe(emp, "{\"phone\":\"  +60 12-345 6789  \"}")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("+60 12-345 6789"))
                .andReturn();
        // Same full detail as GET /api/employees/me.
        JsonNode patched = om.readTree(res.getResponse().getContentAsString());
        assertEquals(me(emp), patched);

        // Absent phone → unchanged.
        patchMe(emp, "{}").andExpect(status().isOk()).andExpect(jsonPath("$.phone").value("+60 12-345 6789"));
        // Blank clears.
        patchMe(emp, "{\"phone\":\"   \"}").andExpect(status().isOk()).andExpect(jsonPath("$.phone").doesNotExist());
        assertTrue(me(emp).get("phone").isNull());
    }

    @Test
    void otherFieldsInBodyAreIgnored() throws Exception {
        String emp = newEmployee("i" + System.nanoTime());
        JsonNode before = me(emp);

        patchMe(emp, """
                {"phone":"0123456789","role":"HR_ADMIN","fullName":"Hacked Name","email":"hacked@example.com",
                 "jobTitle":"CEO","department":"Board","monthlySalary":99999,"active":false,"staffId":"X-1",
                 "password":"hacked-password"}""")
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.phone").value("0123456789"));

        JsonNode after = me(emp);
        assertEquals("0123456789", after.get("phone").asText());
        // Everything except the phone is exactly as it was.
        ((com.fasterxml.jackson.databind.node.ObjectNode) before).remove("phone");
        ((com.fasterxml.jackson.databind.node.ObjectNode) after).remove("phone");
        assertEquals(before, after);
        assertEquals("EMPLOYEE", after.get("role").asText());
        assertEquals("Self Emp", after.get("fullName").asText());
    }

    @Test
    void tooLongPhone_400() throws Exception {
        String emp = newEmployee("l" + System.nanoTime());
        patchMe(emp, "{\"phone\":\"%s\"}".formatted("1".repeat(33))).andExpect(status().isBadRequest());
        patchMe(emp, "{\"phone\":\"%s\"}".formatted("1".repeat(32))).andExpect(status().isOk());
    }

    @Test
    void employeeStillCannotUseAdminUpdate_403() throws Exception {
        String emp = newEmployee("f" + System.nanoTime());
        String id = me(emp).get("id").asText();
        mvc.perform(patch("/api/employees/" + id).header("Authorization", bearer(emp))
                        .contentType("application/json").content("{\"jobTitle\":\"CEO\"}"))
                .andExpect(status().isForbidden());
    }
}
