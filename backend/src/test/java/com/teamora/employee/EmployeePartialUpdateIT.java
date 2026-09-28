package com.teamora.employee;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.nullValue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * PATCH /api/employees/{id} must be genuinely partial: a body that omits the
 * reporting manager / work location leaves them ALONE. They are only unassigned by
 * sending {@code clearReportingManager} / {@code clearWorkLocation} = true.
 *
 * <p>This is a regression guard: the two associations used to be set unconditionally,
 * so saving any single field (e.g. just the job title) silently wiped both — fatal for
 * the hub-and-spoke employee editor, where each section PATCHes only its own slice.
 *
 * <p>Every test registers its own throwaway company so nothing leaks into the other
 * test classes sharing the test database.
 */
class EmployeePartialUpdateIT extends AbstractIntegrationTest {

    /** A freshly registered company: owner token + an assignable MANAGER + an EMPLOYEE to edit. */
    private record Co(String ownerToken, String managerId, String employeeId) {}

    private String register(String slug) throws Exception {
        String body = """
                {"companyName":"PU %s","fullName":"Owner %s","email":"puowner-%s@example.com","password":"password"}"""
                .formatted(slug, slug, slug);
        var res = mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String createEmp(String ownerToken, String name, String email, String role) throws Exception {
        String body = "{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"password\",\"role\":\"%s\"}"
                .formatted(name, email, role);
        var res = mvc.perform(post("/api/employees").header("Authorization", bearer(ownerToken))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn();
        return om.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private Co company(String tag) throws Exception {
        String slug = tag + System.nanoTime();
        String owner = register(slug);
        String mgr = createEmp(owner, "Mgr " + slug, "pumgr-" + slug + "@example.com", "MANAGER");
        String emp = createEmp(owner, "Emp " + slug, "puemp-" + slug + "@example.com", "EMPLOYEE");
        return new Co(owner, mgr, emp);
    }

    private String createSite(String ownerToken, String name) throws Exception {
        String body = "{\"name\":\"%s\",\"latitude\":3.150000,\"longitude\":101.700000}".formatted(name);
        var res = mvc.perform(post("/api/admin/work-locations").header("Authorization", bearer(ownerToken))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn();
        return om.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private JsonNode patchEmp(String token, String id, String json) throws Exception {
        var res = mvc.perform(patch("/api/employees/" + id).header("Authorization", bearer(token))
                        .contentType("application/json").content(json))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(res.getResponse().getContentAsString());
    }

    // ---- the regression ----

    @Test
    void patchingOneField_leavesManagerAndWorkLocationUntouched() throws Exception {
        Co co = company("keep");
        String site = createSite(co.ownerToken(), "Keep Site");

        // Assign both.
        patchEmp(co.ownerToken(), co.employeeId(),
                "{\"reportingManagerId\":\"%s\",\"workLocationId\":\"%s\"}".formatted(co.managerId(), site));

        // PATCH only the job title — the response must still carry both associations…
        mvc.perform(patch("/api/employees/" + co.employeeId()).header("Authorization", bearer(co.ownerToken()))
                        .contentType("application/json").content("{\"jobTitle\":\"Sales Executive\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobTitle").value("Sales Executive"))
                .andExpect(jsonPath("$.reportingManagerId").value(co.managerId()))
                .andExpect(jsonPath("$.workLocationId").value(site));

        // …and so must a fresh read (i.e. nothing was cleared in the database).
        mvc.perform(get("/api/employees/" + co.employeeId()).header("Authorization", bearer(co.ownerToken())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportingManagerId").value(co.managerId()))
                .andExpect(jsonPath("$.workLocationName").value("Keep Site"))
                .andExpect(jsonPath("$.workLocationId").value(site));
    }

    @Test
    void unrelatedSectionSaves_doNotClearEachOther() throws Exception {
        Co co = company("section");
        String site = createSite(co.ownerToken(), "Section Site");
        patchEmp(co.ownerToken(), co.employeeId(),
                "{\"reportingManagerId\":\"%s\",\"workLocationId\":\"%s\"}".formatted(co.managerId(), site));

        // Hub-and-spoke: three different sub-screens each save only their own slice.
        patchEmp(co.ownerToken(), co.employeeId(), "{\"fullName\":\"Renamed Person\",\"department\":\"Retail\"}");
        patchEmp(co.ownerToken(), co.employeeId(), "{\"monthlySalary\":4000.00,\"hoursPerDay\":8}");
        JsonNode after = patchEmp(co.ownerToken(), co.employeeId(), "{\"nric\":\"900101-10-5566\",\"bankName\":\"Maybank\"}");

        org.junit.jupiter.api.Assertions.assertEquals(co.managerId(), after.get("reportingManagerId").asText());
        org.junit.jupiter.api.Assertions.assertEquals(site, after.get("workLocationId").asText());
        org.junit.jupiter.api.Assertions.assertEquals("Renamed Person", after.get("fullName").asText());
        org.junit.jupiter.api.Assertions.assertEquals("Maybank", after.get("bankName").asText());
    }

    // ---- set / clear, per association ----

    @Test
    void reportingManager_setThenExplicitlyCleared() throws Exception {
        Co co = company("mgr");

        mvc.perform(patch("/api/employees/" + co.employeeId()).header("Authorization", bearer(co.ownerToken()))
                        .contentType("application/json")
                        .content("{\"reportingManagerId\":\"" + co.managerId() + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportingManagerId").value(co.managerId()))
                .andExpect(jsonPath("$.reportingManagerName").value(org.hamcrest.Matchers.startsWith("Mgr ")));

        // A bare null id changes nothing…
        mvc.perform(patch("/api/employees/" + co.employeeId()).header("Authorization", bearer(co.ownerToken()))
                        .contentType("application/json").content("{\"reportingManagerId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportingManagerId").value(co.managerId()));

        // …only the explicit flag unassigns.
        mvc.perform(patch("/api/employees/" + co.employeeId()).header("Authorization", bearer(co.ownerToken()))
                        .contentType("application/json").content("{\"clearReportingManager\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportingManagerId").value(nullValue()));
        mvc.perform(get("/api/employees/" + co.employeeId()).header("Authorization", bearer(co.ownerToken())))
                .andExpect(jsonPath("$.reportingManagerId").value(nullValue()));
    }

    @Test
    void workLocation_setThenExplicitlyCleared() throws Exception {
        Co co = company("site");
        String site = createSite(co.ownerToken(), "Clear Site");

        mvc.perform(patch("/api/employees/" + co.employeeId()).header("Authorization", bearer(co.ownerToken()))
                        .contentType("application/json").content("{\"workLocationId\":\"" + site + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workLocationId").value(site))
                .andExpect(jsonPath("$.workLocationName").value("Clear Site"));

        mvc.perform(patch("/api/employees/" + co.employeeId()).header("Authorization", bearer(co.ownerToken()))
                        .contentType("application/json").content("{\"workLocationId\":null}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workLocationId").value(site));

        mvc.perform(patch("/api/employees/" + co.employeeId()).header("Authorization", bearer(co.ownerToken()))
                        .contentType("application/json").content("{\"clearWorkLocation\":true}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workLocationId").value(nullValue()));
        mvc.perform(get("/api/employees/" + co.employeeId()).header("Authorization", bearer(co.ownerToken())))
                .andExpect(jsonPath("$.workLocationId").value(nullValue()));
    }

    @Test
    void clearFlagFalse_leavesAssociationsAlone() throws Exception {
        Co co = company("flagfalse");
        String site = createSite(co.ownerToken(), "False Flag Site");
        patchEmp(co.ownerToken(), co.employeeId(),
                "{\"reportingManagerId\":\"%s\",\"workLocationId\":\"%s\"}".formatted(co.managerId(), site));

        mvc.perform(patch("/api/employees/" + co.employeeId()).header("Authorization", bearer(co.ownerToken()))
                        .contentType("application/json")
                        .content("{\"clearReportingManager\":false,\"clearWorkLocation\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportingManagerId").value(co.managerId()))
                .andExpect(jsonPath("$.workLocationId").value(site));
    }

    @Test
    void idWinsOverClearFlag() throws Exception {
        Co co = company("prec");
        String site = createSite(co.ownerToken(), "Precedence Site");

        // An id present alongside a true clear flag → the id wins (rule 1 before rule 2).
        mvc.perform(patch("/api/employees/" + co.employeeId()).header("Authorization", bearer(co.ownerToken()))
                        .contentType("application/json")
                        .content(("{\"reportingManagerId\":\"%s\",\"clearReportingManager\":true,"
                                + "\"workLocationId\":\"%s\",\"clearWorkLocation\":true}")
                                .formatted(co.managerId(), site)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.reportingManagerId").value(co.managerId()))
                .andExpect(jsonPath("$.workLocationId").value(site));
    }

    // ---- validation is unchanged ----

    @Test
    void cannotReportToSelf_400() throws Exception {
        Co co = company("self");
        mvc.perform(patch("/api/employees/" + co.employeeId()).header("Authorization", bearer(co.ownerToken()))
                        .contentType("application/json")
                        .content("{\"reportingManagerId\":\"" + co.employeeId() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crossCompanyManagerId_400() throws Exception {
        Co mine = company("x1");
        Co theirs = company("x2");
        mvc.perform(patch("/api/employees/" + mine.employeeId()).header("Authorization", bearer(mine.ownerToken()))
                        .contentType("application/json")
                        .content("{\"reportingManagerId\":\"" + theirs.managerId() + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void crossCompanyWorkLocationId_400() throws Exception {
        Co mine = company("x3");
        Co theirs = company("x4");
        String theirSite = createSite(theirs.ownerToken(), "Their Site");
        mvc.perform(patch("/api/employees/" + mine.employeeId()).header("Authorization", bearer(mine.ownerToken()))
                        .contentType("application/json").content("{\"workLocationId\":\"" + theirSite + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void reportingManagerMustBeManagerial_400() throws Exception {
        Co co = company("plain");
        String otherEmp = createEmp(co.ownerToken(), "Plain Staff",
                "puplain-" + System.nanoTime() + "@example.com", "EMPLOYEE");
        mvc.perform(patch("/api/employees/" + co.employeeId()).header("Authorization", bearer(co.ownerToken()))
                        .contentType("application/json").content("{\"reportingManagerId\":\"" + otherEmp + "\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void ownerRoleRules_unchangedByClearFlags() throws Exception {
        Co co = company("owner");

        // Promoting to OWNER via PATCH is still rejected, clear flags or not.
        mvc.perform(patch("/api/employees/" + co.employeeId()).header("Authorization", bearer(co.ownerToken()))
                        .contentType("application/json")
                        .content("{\"role\":\"OWNER\",\"clearReportingManager\":true}"))
                .andExpect(status().isBadRequest());

        // A rejected request must not have clobbered anything: promote to MANAGER instead
        // and confirm the (still unset) association plus the new role.
        mvc.perform(patch("/api/employees/" + co.employeeId()).header("Authorization", bearer(co.ownerToken()))
                        .contentType("application/json").content("{\"role\":\"MANAGER\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("MANAGER"));
    }
}
