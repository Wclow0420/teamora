package com.teamora.auth;

import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Auth, RBAC, and multi-tenant isolation — the security-critical behaviours. */
class AuthAndTenantIT extends AbstractIntegrationTest {

    @Test
    void login_returnsRoleAndCompany() throws Exception {
        String token = login("sarah@lumi.com", "password");
        mvc.perform(get("/api/auth/me").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("HR_ADMIN"))
                .andExpect(jsonPath("$.companyName").value("Lumi Foods Sdn Bhd"));
    }

    @Test
    void badCredentials_return401() throws Exception {
        mvc.perform(post("/api/auth/login").contentType("application/json")
                        .content("{\"email\":\"amir@lumi.com\",\"password\":\"wrong\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void employee_cannotAccessAdminEndpoints() throws Exception {
        String token = login("amir@lumi.com", "password");
        mvc.perform(get("/api/employees").header("Authorization", bearer(token)))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_isRejected() throws Exception {
        mvc.perform(get("/api/employees/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void register_createsCompanyAndOwner_thenIsolatedFromOtherTenants() throws Exception {
        String unique = "acme-" + System.nanoTime();
        String email = unique + "@example.com";
        String body = """
                {"companyName":"Acme %s","fullName":"Jane Tan","email":"%s","password":"password"}"""
                .formatted(unique, email);

        var res = mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employee.role").value("OWNER"))
                .andReturn();
        String token = om.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();

        // The new tenant only sees its own single employee — not Lumi Foods' staff.
        mvc.perform(get("/api/employees").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].email").value(email));

        // And cannot find another tenant's people by search.
        mvc.perform(get("/api/employees").param("q", "amir").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void duplicateEmail_onRegister_isRejected() throws Exception {
        mvc.perform(post("/api/auth/register").contentType("application/json")
                        .content("{\"companyName\":\"Dup Co\",\"fullName\":\"X\",\"email\":\"amir@lumi.com\",\"password\":\"password\"}"))
                .andExpect(status().isBadRequest());
    }
}
