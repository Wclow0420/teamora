package com.teamora.calendar;

import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.YearMonth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Staff calendar: month grid + upcoming list, aggregating company events + own leave. */
class CalendarIT extends AbstractIntegrationTest {

    @Test
    void calendar_returnsMonthGridAndUpcoming() throws Exception {
        String token = login("sarah@lumi.com", "password");
        String month = YearMonth.now().toString(); // "yyyy-MM"

        mvc.perform(get("/api/calendar").param("month", month).header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthLabel").isNotEmpty())
                .andExpect(jsonPath("$.events").isMap())
                .andExpect(jsonPath("$.events.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.upcoming").isArray())
                .andExpect(jsonPath("$.upcoming.length()").value(org.hamcrest.Matchers.greaterThan(0)))
                .andExpect(jsonPath("$.upcoming[0].iconName").isNotEmpty())
                .andExpect(jsonPath("$.upcoming[0].accentColorKey").isNotEmpty());
    }

    @Test
    void calendar_defaultsToCurrentMonthWhenNoParam() throws Exception {
        String token = login("sarah@lumi.com", "password");
        mvc.perform(get("/api/calendar").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthLabel").isNotEmpty());
    }

    @Test
    void calendar_requiresAuthentication() throws Exception {
        mvc.perform(get("/api/calendar"))
                .andExpect(status().isUnauthorized());
    }
}
