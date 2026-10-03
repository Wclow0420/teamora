package com.teamora.company;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** First-run setup wizard completion flag on the company. */
class CompanySetupIT extends AbstractIntegrationTest {

    private JsonNode register(String slug) throws Exception {
        String body = """
                {"companyName":"Setup %s","fullName":"Owner %s","email":"setup-%s@example.com","password":"password"}"""
                .formatted(slug, slug, slug);
        var res = mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(res.getResponse().getContentAsString());
    }

    @Test
    void newCompany_startsUnfinished_thenCompletes_idempotently() throws Exception {
        String owner = register("a").get("accessToken").asText();

        mvc.perform(get("/api/companies/me").header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupCompletedAt").doesNotExist());

        mvc.perform(post("/api/companies/me/setup-complete").header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());
        var first = om.readTree(mvc.perform(get("/api/companies/me").header("Authorization", bearer(owner)))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString())
                .get("setupCompletedAt").asText();

        // A second call keeps the original completion time.
        mvc.perform(post("/api/companies/me/setup-complete").header("Authorization", bearer(owner)))
                .andExpect(status().isNoContent());
        mvc.perform(get("/api/companies/me").header("Authorization", bearer(owner)))
                .andExpect(jsonPath("$.setupCompletedAt").value(first));
    }

    @Test
    void seededCompany_isAlreadySetUp() throws Exception {
        mvc.perform(get("/api/companies/me").header("Authorization", bearer(login("sarah@lumi.com", "password"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.setupCompletedAt").isNotEmpty());
    }

    @Test
    void permissions() throws Exception {
        mvc.perform(post("/api/companies/me/setup-complete")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/companies/me/setup-complete").header("Authorization", bearer(login("amir@lumi.com", "password"))))
                .andExpect(status().isForbidden());
    }
}
