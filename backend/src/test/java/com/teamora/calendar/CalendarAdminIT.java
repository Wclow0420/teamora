package com.teamora.calendar;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Admin company-calendar CRUD: OWNER/HR_ADMIN author events (managers can read but not
 * mutate), rows are tenant-scoped, validation is enforced, and what an admin creates shows
 * up on the staff calendar for that month.
 *
 * <p>Fixtures live in a freshly-registered company and six months out from today, so no
 * payroll period under test ever gains a HOLIDAY (which would change computed pay).
 */
class CalendarAdminIT extends AbstractIntegrationTest {

    /** A month far enough ahead that no payroll/attendance fixture touches it. */
    private static final YearMonth MONTH = YearMonth.now().plusMonths(6);
    private static final String MONTH_PARAM = MONTH.toString();      // "yyyy-MM"
    private static final String EVENT_DATE = MONTH.atDay(15).toString();

    private record Co(String owner, String hr, String mgr, String emp) {}

    private String register(String slug) throws Exception {
        String body = """
                {"companyName":"CAL %s","fullName":"Owner %s","email":"calowner-%s@example.com","password":"password"}"""
                .formatted(slug, slug, slug);
        var res = mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private void createEmp(String owner, String name, String email, String role) throws Exception {
        String body = "{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"password\",\"role\":\"%s\"}"
                .formatted(name, email, role);
        mvc.perform(post("/api/employees").header("Authorization", bearer(owner))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated());
    }

    private Co setup(String slug) throws Exception {
        String owner = register(slug);
        createEmp(owner, "HR " + slug, "calhr-" + slug + "@example.com", "HR_ADMIN");
        createEmp(owner, "Mgr " + slug, "calmgr-" + slug + "@example.com", "MANAGER");
        createEmp(owner, "Emp " + slug, "calemp-" + slug + "@example.com", "EMPLOYEE");
        return new Co(owner,
                login("calhr-" + slug + "@example.com", "password"),
                login("calmgr-" + slug + "@example.com", "password"),
                login("calemp-" + slug + "@example.com", "password"));
    }

    private static String holidayBody(String title) {
        return "{\"title\":\"%s\",\"eventDate\":\"%s\",\"eventType\":\"HOLIDAY\"}".formatted(title, EVENT_DATE);
    }

    @Test
    void endpoints_requireAuthentication() throws Exception {
        String someId = "00000000-0000-0000-0000-000000000000";
        mvc.perform(get("/api/admin/calendar/events")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/admin/calendar/events").contentType("application/json")
                        .content(holidayBody("Nope"))).andExpect(status().isUnauthorized());
        mvc.perform(patch("/api/admin/calendar/events/" + someId).contentType("application/json")
                        .content("{\"title\":\"Nope\"}")).andExpect(status().isUnauthorized());
        mvc.perform(delete("/api/admin/calendar/events/" + someId)).andExpect(status().isUnauthorized());
    }

    @Test
    void hrCreatesHoliday_showsOnBothCalendars_thenPatchAndDelete() throws Exception {
        Co co = setup("h" + System.nanoTime());

        // HR_ADMIN creates a public holiday.
        var res = mvc.perform(post("/api/admin/calendar/events").header("Authorization", bearer(co.hr()))
                        .contentType("application/json")
                        .content("{\"title\":\"Hari Raya Haji\",\"eventDate\":\"%s\",\"eventType\":\"HOLIDAY\",\"timeLabel\":\"All day\"}"
                                .formatted(EVENT_DATE)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.title").value("Hari Raya Haji"))
                .andExpect(jsonPath("$.eventDate").value(EVENT_DATE))
                .andExpect(jsonPath("$.eventType").value("HOLIDAY"))
                .andExpect(jsonPath("$.accentColorKey").value("amber"))
                .andExpect(jsonPath("$.iconName").value("star"))
                .andExpect(jsonPath("$.timeLabel").value("All day"))
                .andReturn();
        JsonNode created = om.readTree(res.getResponse().getContentAsString());
        String id = created.get("id").asText();

        // It appears in the admin month listing …
        mvc.perform(get("/api/admin/calendar/events").param("month", MONTH_PARAM)
                        .header("Authorization", bearer(co.hr())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + id + "')]").isNotEmpty());

        // … and on the staff calendar grid for that month (day 15 → an amber dot).
        mvc.perform(get("/api/calendar").param("month", MONTH_PARAM)
                        .header("Authorization", bearer(co.emp())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events['15']").isNotEmpty())
                .andExpect(jsonPath("$.events['15'][0]").value("amber"));

        // PATCH updates title + date within the same month.
        String moved = MONTH.atDay(16).toString();
        mvc.perform(patch("/api/admin/calendar/events/" + id).header("Authorization", bearer(co.hr()))
                        .contentType("application/json")
                        .content("{\"title\":\"Hari Raya Haji (observed)\",\"eventDate\":\"%s\"}".formatted(moved)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Hari Raya Haji (observed)"))
                .andExpect(jsonPath("$.eventDate").value(moved))
                .andExpect(jsonPath("$.eventType").value("HOLIDAY"));

        mvc.perform(get("/api/calendar").param("month", MONTH_PARAM)
                        .header("Authorization", bearer(co.emp())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events['15']").doesNotExist())
                .andExpect(jsonPath("$.events['16']").isNotEmpty());

        // DELETE → 204, gone from both lists.
        mvc.perform(delete("/api/admin/calendar/events/" + id).header("Authorization", bearer(co.hr())))
                .andExpect(status().isNoContent());

        mvc.perform(get("/api/admin/calendar/events").param("month", MONTH_PARAM)
                        .header("Authorization", bearer(co.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + id + "')]").isEmpty());
        mvc.perform(get("/api/calendar").param("month", MONTH_PARAM)
                        .header("Authorization", bearer(co.emp())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.events['16']").doesNotExist());
    }

    @Test
    void managerCanReadButNotMutate_employeeCannotRead() throws Exception {
        Co co = setup("m" + System.nanoTime());

        // Owner authors one so there's something to attempt against.
        var res = mvc.perform(post("/api/admin/calendar/events").header("Authorization", bearer(co.owner()))
                        .contentType("application/json").content(holidayBody("Company day")))
                .andExpect(status().isCreated()).andReturn();
        String id = om.readTree(res.getResponse().getContentAsString()).get("id").asText();

        // Manager: reads are fine (admin URL rule), mutations are OWNER/HR_ADMIN only.
        mvc.perform(get("/api/admin/calendar/events").param("month", MONTH_PARAM)
                        .header("Authorization", bearer(co.mgr())))
                .andExpect(status().isOk());
        mvc.perform(post("/api/admin/calendar/events").header("Authorization", bearer(co.mgr()))
                        .contentType("application/json").content(holidayBody("Manager holiday")))
                .andExpect(status().isForbidden());
        mvc.perform(patch("/api/admin/calendar/events/" + id).header("Authorization", bearer(co.mgr()))
                        .contentType("application/json").content("{\"title\":\"Renamed\"}"))
                .andExpect(status().isForbidden());
        mvc.perform(delete("/api/admin/calendar/events/" + id).header("Authorization", bearer(co.mgr())))
                .andExpect(status().isForbidden());

        // Employee is outside /api/admin/** entirely.
        mvc.perform(get("/api/admin/calendar/events").header("Authorization", bearer(co.emp())))
                .andExpect(status().isForbidden());
        mvc.perform(post("/api/admin/calendar/events").header("Authorization", bearer(co.emp()))
                        .contentType("application/json").content(holidayBody("Staff holiday")))
                .andExpect(status().isForbidden());
    }

    @Test
    void create_rejectsBlankTitle_missingDate_andBirthdayType() throws Exception {
        Co co = setup("v" + System.nanoTime());
        String hr = co.hr();

        mvc.perform(post("/api/admin/calendar/events").header("Authorization", bearer(hr))
                        .contentType("application/json")
                        .content("{\"title\":\"  \",\"eventDate\":\"%s\",\"eventType\":\"EVENT\"}".formatted(EVENT_DATE)))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/admin/calendar/events").header("Authorization", bearer(hr))
                        .contentType("application/json")
                        .content("{\"title\":\"No date\",\"eventType\":\"EVENT\"}"))
                .andExpect(status().isBadRequest());

        mvc.perform(post("/api/admin/calendar/events").header("Authorization", bearer(hr))
                        .contentType("application/json")
                        .content("{\"title\":\"No type\",\"eventDate\":\"%s\"}".formatted(EVENT_DATE)))
                .andExpect(status().isBadRequest());

        // Birthdays are derived from employee records — never hand-authored.
        mvc.perform(post("/api/admin/calendar/events").header("Authorization", bearer(hr))
                        .contentType("application/json")
                        .content("{\"title\":\"Amir's birthday\",\"eventDate\":\"%s\",\"eventType\":\"BIRTHDAY\"}"
                                .formatted(EVENT_DATE)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void update_rejectsBlankTitleAndBirthdayType() throws Exception {
        Co co = setup("u" + System.nanoTime());
        var res = mvc.perform(post("/api/admin/calendar/events").header("Authorization", bearer(co.hr()))
                        .contentType("application/json").content(holidayBody("Founders day")))
                .andExpect(status().isCreated()).andReturn();
        String id = om.readTree(res.getResponse().getContentAsString()).get("id").asText();

        mvc.perform(patch("/api/admin/calendar/events/" + id).header("Authorization", bearer(co.hr()))
                        .contentType("application/json").content("{\"title\":\"   \"}"))
                .andExpect(status().isBadRequest());
        mvc.perform(patch("/api/admin/calendar/events/" + id).header("Authorization", bearer(co.hr()))
                        .contentType("application/json").content("{\"eventType\":\"BIRTHDAY\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void eventsAreTenantScoped_foreignAdminGets404() throws Exception {
        Co a = setup("t1-" + System.nanoTime());
        var res = mvc.perform(post("/api/admin/calendar/events").header("Authorization", bearer(a.owner()))
                        .contentType("application/json").content(holidayBody("A-Only Holiday")))
                .andExpect(status().isCreated()).andReturn();
        String id = om.readTree(res.getResponse().getContentAsString()).get("id").asText();

        Co b = setup("t2-" + System.nanoTime());

        // Company B's admin can't see it, patch it, or delete it.
        mvc.perform(get("/api/admin/calendar/events").param("month", MONTH_PARAM)
                        .header("Authorization", bearer(b.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.title=='A-Only Holiday')]").isEmpty());
        mvc.perform(patch("/api/admin/calendar/events/" + id).header("Authorization", bearer(b.owner()))
                        .contentType("application/json").content("{\"title\":\"Hijacked\"}"))
                .andExpect(status().isNotFound());
        mvc.perform(delete("/api/admin/calendar/events/" + id).header("Authorization", bearer(b.owner())))
                .andExpect(status().isNotFound());

        // Still intact for company A.
        mvc.perform(get("/api/admin/calendar/events").param("month", MONTH_PARAM)
                        .header("Authorization", bearer(a.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id=='" + id + "')]").isNotEmpty());
    }

    @Test
    void list_defaultsToCurrentMonth() throws Exception {
        Co co = setup("d" + System.nanoTime());
        mvc.perform(get("/api/admin/calendar/events").header("Authorization", bearer(co.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }
}
