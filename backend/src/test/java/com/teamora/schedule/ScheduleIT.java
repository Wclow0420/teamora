package com.teamora.schedule;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.temporal.TemporalAdjusters;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Assign a shift then read it back in the week; employees are barred from admin scheduling. */
class ScheduleIT extends AbstractIntegrationTest {

    private JsonNode register(String slug) throws Exception {
        String body = """
                {"companyName":"Sch %s","fullName":"Owner %s","email":"schowner-%s@example.com","password":"password"}"""
                .formatted(slug, slug, slug);
        var res = mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(res.getResponse().getContentAsString());
    }

    private String createEmp(String ownerToken, String name, String email) throws Exception {
        String body = "{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"password\",\"role\":\"EMPLOYEE\"}"
                .formatted(name, email);
        var res = mvc.perform(post("/api/employees").header("Authorization", bearer(ownerToken))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn();
        return om.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    @Test
    void assign_thenWeekShowsShift() throws Exception {
        String slug = "sch" + System.nanoTime();
        String ownerToken = register(slug).get("accessToken").asText();
        String empId = createEmp(ownerToken, "Emp A", "scha-" + slug + "@example.com");

        LocalDate monday = LocalDate.now().with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate tuesday = monday.plusDays(1);

        // Assign a MORNING shift on Tuesday.
        mvc.perform(post("/api/admin/schedule").header("Authorization", bearer(ownerToken))
                        .contentType("application/json")
                        .content("{\"employeeId\":\"%s\",\"date\":\"%s\",\"shiftType\":\"MORNING\"}"
                                .formatted(empId, tuesday)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.employeeName").value("Emp A"))
                .andExpect(jsonPath("$.shiftType").value("MORNING"))
                .andExpect(jsonPath("$.shiftLabel").value("Morning"))
                .andExpect(jsonPath("$.shiftColorKey").value("coral"));

        // The week (starting this Monday) shows it on Tuesday (index 1).
        mvc.perform(get("/api/admin/schedule").header("Authorization", bearer(ownerToken))
                        .param("weekStart", monday.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days.length()").value(7))
                .andExpect(jsonPath("$.days[1].onShift").value(1))
                .andExpect(jsonPath("$.days[1].shifts[0].employeeName").value("Emp A"))
                .andExpect(jsonPath("$.days[1].weekdayLabel").value("Tue"));

        // Upsert: re-assign same day to OFF removes it from the roster.
        mvc.perform(post("/api/admin/schedule").header("Authorization", bearer(ownerToken))
                        .contentType("application/json")
                        .content("{\"employeeId\":\"%s\",\"date\":\"%s\",\"shiftType\":\"OFF\"}"
                                .formatted(empId, tuesday)))
                .andExpect(status().isOk());
        mvc.perform(get("/api/admin/schedule").header("Authorization", bearer(ownerToken))
                        .param("weekStart", monday.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[1].onShift").value(0));
    }

    @Test
    void employeeCannotAccessSchedule() throws Exception {
        String slug = "sch" + System.nanoTime();
        String ownerToken = register(slug).get("accessToken").asText();
        createEmp(ownerToken, "Emp", "schemp-" + slug + "@example.com");
        String empToken = login("schemp-" + slug + "@example.com", "password");
        mvc.perform(get("/api/admin/schedule").header("Authorization", bearer(empToken)))
                .andExpect(status().isForbidden());
    }
}
