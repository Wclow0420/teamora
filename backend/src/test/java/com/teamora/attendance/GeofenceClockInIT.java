package com.teamora.attendance;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Server-side geofenced clock-in. Uses a fresh company + fresh employees per
 * scenario so it never touches shared seeded attendance (clock-in is once/day).
 *
 * A geofence pin at (3.150000, 101.700000) with the default 100 m radius:
 *  - assigned + inside  → OK,
 *  - assigned + outside → 400 with a distance message,
 *  - assigned + no coords → 400 (location required),
 *  - unassigned → OK without coords,
 *  - assigned but site inactive → OK without coords (don't lock people out).
 */
class GeofenceClockInIT extends AbstractIntegrationTest {

    private static final double SITE_LAT = 3.150000;
    private static final double SITE_LNG = 101.700000;

    private String owner;

    private String register(String slug) throws Exception {
        String body = """
                {"companyName":"GF %s","fullName":"Owner %s","email":"gfowner-%s@example.com","password":"password"}"""
                .formatted(slug, slug, slug);
        var res = mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    /** Create an EMPLOYEE in the owner's company and return its id. */
    private String createEmp(String email) throws Exception {
        String body = "{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"password\",\"role\":\"EMPLOYEE\"}"
                .formatted(email, email);
        var res = mvc.perform(post("/api/employees").header("Authorization", bearer(owner))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn();
        return om.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private String createSite(String name, boolean active) throws Exception {
        String body = "{\"name\":\"%s\",\"latitude\":%s,\"longitude\":%s,\"active\":%s}"
                .formatted(name, SITE_LAT, SITE_LNG, active);
        var res = mvc.perform(post("/api/admin/work-locations").header("Authorization", bearer(owner))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn();
        return om.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private void assign(String empId, String siteId) throws Exception {
        JsonNode res = om.readTree(mvc.perform(patch("/api/employees/" + empId).header("Authorization", bearer(owner))
                        .contentType("application/json").content("{\"workLocationId\":\"" + siteId + "\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.workLocationId").value(siteId))
                .andReturn().getResponse().getContentAsString());
        // sanity: name round-trips
        org.junit.jupiter.api.Assertions.assertNotNull(res.get("workLocationName").asText());
    }

    private String clockInBody(Double lat, Double lng) {
        return "{\"latitude\":%s,\"longitude\":%s}".formatted(lat, lng);
    }

    @Test
    void assignedInsideRadius_clocksInOk() throws Exception {
        owner = register("in" + System.nanoTime());
        String site = createSite("Shop In", true);
        String empId = createEmp("gf-in-" + System.nanoTime() + "@example.com");
        String empEmail = om.readTree(mvc.perform(patch("/api/employees/" + empId).header("Authorization", bearer(owner))
                        .contentType("application/json").content("{\"workLocationId\":\"" + site + "\"}"))
                .andExpect(status().isOk()).andReturn().getResponse().getContentAsString()).get("email").asText();
        String emp = login(empEmail, "password");

        // ~2 m from the pin.
        mvc.perform(post("/api/attendance/clock-in").header("Authorization", bearer(emp))
                        .contentType("application/json").content(clockInBody(SITE_LAT + 0.00001, SITE_LNG + 0.00001)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is("PRESENT"), org.hamcrest.Matchers.is("LATE"))))
                .andExpect(jsonPath("$.location").value("Shop In"));
    }

    @Test
    void assignedOutsideRadius_rejectedWithDistanceMessage() throws Exception {
        owner = register("out" + System.nanoTime());
        String site = createSite("Shop A", true);
        String email = "gf-out-" + System.nanoTime() + "@example.com";
        String empId = createEmp(email);
        assign(empId, site);
        String emp = login(email, "password");

        // Far away (~5 km+) → out of the 100 m fence.
        mvc.perform(post("/api/attendance/clock-in").header("Authorization", bearer(emp))
                        .contentType("application/json").content(clockInBody(SITE_LAT + 0.05, SITE_LNG + 0.05)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Shop A")))
                .andExpect(jsonPath("$.message").value(containsString("Move within 100 m")));
    }

    @Test
    void assignedWithoutCoords_rejected() throws Exception {
        owner = register("nc" + System.nanoTime());
        String site = createSite("Shop NoCoords", true);
        String email = "gf-nc-" + System.nanoTime() + "@example.com";
        String empId = createEmp(email);
        assign(empId, site);
        String emp = login(email, "password");

        // No body at all → geofenced staff must supply coordinates.
        mvc.perform(post("/api/attendance/clock-in").header("Authorization", bearer(emp)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Location required")));
    }

    @Test
    void unassignedEmployee_clocksInWithoutCoords() throws Exception {
        owner = register("un" + System.nanoTime());
        String email = "gf-un-" + System.nanoTime() + "@example.com";
        createEmp(email);
        String emp = login(email, "password");

        mvc.perform(post("/api/attendance/clock-in").header("Authorization", bearer(emp)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value(org.hamcrest.Matchers.anyOf(
                        org.hamcrest.Matchers.is("PRESENT"), org.hamcrest.Matchers.is("LATE"))));
    }

    @Test
    void inactiveSite_treatedAsNoGeofence() throws Exception {
        owner = register("ia" + System.nanoTime());
        String site = createSite("Closed Shop", false);
        String email = "gf-ia-" + System.nanoTime() + "@example.com";
        String empId = createEmp(email);
        assign(empId, site);
        String emp = login(email, "password");

        // Assigned to an INACTIVE site → no geofence, clock-in works without coords.
        mvc.perform(post("/api/attendance/clock-in").header("Authorization", bearer(emp)))
                .andExpect(status().isOk());
    }
}
