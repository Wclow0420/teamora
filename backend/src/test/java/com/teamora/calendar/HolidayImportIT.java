package com.teamora.calendar;

import com.teamora.AbstractIntegrationTest;
import com.teamora.common.Zones;
import org.junit.jupiter.api.Test;

import java.time.LocalDate;

import static org.hamcrest.Matchers.contains;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.hasItems;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Malaysian public holiday import: the suggested list (flagged {@code alreadyAdded} per
 * company), the confirm-to-import endpoint (creates HOLIDAY events, skips duplicates),
 * OWNER/HR_ADMIN-only access, tenant isolation and validation.
 *
 * <p>Every test registers its own company, so the 2027 HOLIDAYs it creates never reach
 * the demo companies' payroll fixtures.
 */
class HolidayImportIT extends AbstractIntegrationTest {

    private static final String SUGGESTIONS = "/api/admin/calendar/holiday-suggestions";
    private static final String IMPORT = "/api/admin/calendar/holiday-import";

    private record Co(String owner, String hr, String mgr, String emp) {}

    private String register(String slug) throws Exception {
        String body = """
                {"companyName":"HOL %s","fullName":"Owner %s","email":"holowner-%s@example.com","password":"password"}"""
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
        createEmp(owner, "HR " + slug, "holhr-" + slug + "@example.com", "HR_ADMIN");
        createEmp(owner, "Mgr " + slug, "holmgr-" + slug + "@example.com", "MANAGER");
        createEmp(owner, "Emp " + slug, "holemp-" + slug + "@example.com", "EMPLOYEE");
        return new Co(owner,
                login("holhr-" + slug + "@example.com", "password"),
                login("holmgr-" + slug + "@example.com", "password"),
                login("holemp-" + slug + "@example.com", "password"));
    }

    private static String importBody(String... dateNamePairs) {
        StringBuilder sb = new StringBuilder("{\"items\":[");
        for (int i = 0; i < dateNamePairs.length; i += 2) {
            if (i > 0) sb.append(',');
            sb.append("{\"date\":\"").append(dateNamePairs[i]).append("\",\"name\":\"")
                    .append(dateNamePairs[i + 1]).append("\"}");
        }
        return sb.append("]}").toString();
    }

    @Test
    void suggestions2027_listTheCatalogue_andFlipAlreadyAddedAfterImport() throws Exception {
        Co a = setup("s" + System.nanoTime());
        Co b = setup("sb" + System.nanoTime());

        mvc.perform(get(SUGGESTIONS).param("year", "2027").header("Authorization", bearer(a.hr())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2027))
                .andExpect(jsonPath("$.source").value(
                        "Based on published Malaysian public holiday lists — check against the official gazette for your state."))
                .andExpect(jsonPath("$.years").value(hasItems(2026, 2027)))
                .andExpect(jsonPath("$.items.length()").value(18))
                .andExpect(jsonPath("$.items[0].date").value("2027-01-01"))
                .andExpect(jsonPath("$.items[0].name").value("New Year's Day"))
                .andExpect(jsonPath("$.items[0].note").value(
                        "Not observed in Johor, Kedah, Kelantan, Perlis, Terengganu"))
                .andExpect(jsonPath("$.items[4].date").value("2027-02-06"))
                .andExpect(jsonPath("$.items[4].name").value("Chinese New Year"))
                .andExpect(jsonPath("$.items[4].note").value(org.hamcrest.Matchers.nullValue()))
                .andExpect(jsonPath("$.items[*].alreadyAdded").value(everyItem(org.hamcrest.Matchers.is(false))));

        // Import two (owner) → both created.
        mvc.perform(post(IMPORT).header("Authorization", bearer(a.owner())).contentType("application/json")
                        .content(importBody("2027-02-06", "Chinese New Year", "2027-02-07", "Chinese New Year (second day)")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(2))
                .andExpect(jsonPath("$.skipped").value(0));

        // Re-import one of them + a new date twice → 1 created, 2 skipped (existing + in-request dup).
        mvc.perform(post(IMPORT).header("Authorization", bearer(a.hr())).contentType("application/json")
                        .content(importBody("2027-02-06", "Chinese New Year", "2027-05-01", "Labour Day",
                                "2027-05-01", "Labour Day")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1))
                .andExpect(jsonPath("$.skipped").value(2));

        // alreadyAdded flips for exactly those three dates.
        mvc.perform(get(SUGGESTIONS).param("year", "2027").header("Authorization", bearer(a.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.alreadyAdded == true)].date")
                        .value(contains("2027-02-06", "2027-02-07", "2027-05-01")))
                .andExpect(jsonPath("$.items[0].alreadyAdded").value(false));

        // They are real HOLIDAY company events, titled by the picked name, exactly once each.
        mvc.perform(get("/api/admin/calendar/events").param("month", "2027-02")
                        .header("Authorization", bearer(a.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].title").value("Chinese New Year"))
                .andExpect(jsonPath("$[0].eventType").value("HOLIDAY"))
                .andExpect(jsonPath("$[0].eventDate").value("2027-02-06"));
        mvc.perform(get("/api/admin/calendar/events").param("month", "2027-05")
                        .header("Authorization", bearer(a.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1));

        // A manually-created HOLIDAY on a catalogue date also counts as already added.
        mvc.perform(post("/api/admin/calendar/events").header("Authorization", bearer(b.owner()))
                        .contentType("application/json")
                        .content("{\"title\":\"Merdeka\",\"eventDate\":\"2027-08-31\",\"eventType\":\"HOLIDAY\"}"))
                .andExpect(status().isCreated());

        // Tenant isolation: company B sees none of A's imports, and nothing was added to B's calendar.
        mvc.perform(get(SUGGESTIONS).param("year", "2027").header("Authorization", bearer(b.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.alreadyAdded == true)].date").value(contains("2027-08-31")));
        mvc.perform(get("/api/admin/calendar/events").param("month", "2027-02")
                        .header("Authorization", bearer(b.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    void suggestions_unknownYearIsEmpty_andYearDefaultsToCurrent() throws Exception {
        Co co = setup("y" + System.nanoTime());
        mvc.perform(get(SUGGESTIONS).param("year", "2031").header("Authorization", bearer(co.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(2031))
                .andExpect(jsonPath("$.items.length()").value(0))
                .andExpect(jsonPath("$.years").value(hasItems(2026, 2027)));
        mvc.perform(get(SUGGESTIONS).header("Authorization", bearer(co.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.year").value(LocalDate.now(Zones.KL).getYear()));
        mvc.perform(get(SUGGESTIONS).param("year", "abc").header("Authorization", bearer(co.owner())))
                .andExpect(status().isBadRequest());
    }

    @Test
    void onlyOwnerAndHr_unauthenticatedIs401() throws Exception {
        Co co = setup("r" + System.nanoTime());
        String body = importBody("2027-05-01", "Labour Day");

        mvc.perform(get(SUGGESTIONS).param("year", "2027")).andExpect(status().isUnauthorized());
        mvc.perform(post(IMPORT).contentType("application/json").content(body)).andExpect(status().isUnauthorized());

        for (String token : new String[]{co.mgr(), co.emp()}) {
            mvc.perform(get(SUGGESTIONS).param("year", "2027").header("Authorization", bearer(token)))
                    .andExpect(status().isForbidden());
            mvc.perform(post(IMPORT).header("Authorization", bearer(token)).contentType("application/json")
                            .content(body))
                    .andExpect(status().isForbidden());
        }

        // Nothing was created by the forbidden attempts.
        mvc.perform(get(SUGGESTIONS).param("year", "2027").header("Authorization", bearer(co.owner())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.alreadyAdded == true)]").isEmpty());
    }

    @Test
    void import_validation() throws Exception {
        Co co = setup("v" + System.nanoTime());
        String hr = bearer(co.hr());

        // Empty / missing items.
        mvc.perform(post(IMPORT).header("Authorization", hr).contentType("application/json")
                .content("{\"items\":[]}")).andExpect(status().isBadRequest());
        mvc.perform(post(IMPORT).header("Authorization", hr).contentType("application/json")
                .content("{}")).andExpect(status().isBadRequest());

        // More than 40 items.
        String[] many = new String[82];
        LocalDate d = LocalDate.of(2027, 3, 1);
        for (int i = 0; i < 41; i++) {
            many[2 * i] = d.plusDays(i).toString();
            many[2 * i + 1] = "Day " + i;
        }
        mvc.perform(post(IMPORT).header("Authorization", hr).contentType("application/json")
                .content(importBody(many))).andExpect(status().isBadRequest());

        // Invalid ISO date, missing date, blank name, name over 100 chars.
        mvc.perform(post(IMPORT).header("Authorization", hr).contentType("application/json")
                .content(importBody("2027-02-30", "Nope"))).andExpect(status().isBadRequest());
        mvc.perform(post(IMPORT).header("Authorization", hr).contentType("application/json")
                .content(importBody("06/02/2027", "Nope"))).andExpect(status().isBadRequest());
        mvc.perform(post(IMPORT).header("Authorization", hr).contentType("application/json")
                .content("{\"items\":[{\"name\":\"No date\"}]}")).andExpect(status().isBadRequest());
        mvc.perform(post(IMPORT).header("Authorization", hr).contentType("application/json")
                .content(importBody("2027-05-01", "   "))).andExpect(status().isBadRequest());
        mvc.perform(post(IMPORT).header("Authorization", hr).contentType("application/json")
                .content(importBody("2027-05-01", "x".repeat(101)))).andExpect(status().isBadRequest());

        // None of the rejected calls created anything; a 100-char name is fine.
        mvc.perform(get(SUGGESTIONS).param("year", "2027").header("Authorization", hr))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.items[?(@.alreadyAdded == true)]").isEmpty());
        mvc.perform(post(IMPORT).header("Authorization", hr).contentType("application/json")
                        .content(importBody("2027-05-01", "x".repeat(100))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.created").value(1));
    }
}
