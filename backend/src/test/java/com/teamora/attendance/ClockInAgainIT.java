package com.teamora.attendance;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.ResultActions;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Clock in again after clocking out on the same day: the record is resumed, the
 * gap becomes break time, and worked time is net of it. Fresh company + employee
 * per scenario. Real time barely moves inside a test, so the record's instants
 * are back-dated through the repository to simulate a working day.
 */
class ClockInAgainIT extends AbstractIntegrationTest {

    private static final ZoneId KL = ZoneId.of("Asia/Kuala_Lumpur");
    private static final double SITE_LAT = 3.150000;
    private static final double SITE_LNG = 101.700000;

    @Autowired
    private AttendanceRepository attendance;

    private record Staff(String ownerToken, String token, UUID id) {}

    private Staff staff(String tag) throws Exception {
        String slug = tag + System.nanoTime();
        var reg = mvc.perform(post("/api/auth/register").contentType("application/json").content("""
                        {"companyName":"CIA %s","fullName":"Owner %s","email":"cia-owner-%s@example.com","password":"password"}"""
                        .formatted(slug, slug, slug)))
                .andExpect(status().isOk()).andReturn();
        String owner = om.readTree(reg.getResponse().getContentAsString()).get("accessToken").asText();
        String email = "cia-emp-" + slug + "@example.com";
        var res = mvc.perform(post("/api/employees").header("Authorization", bearer(owner))
                        .contentType("application/json")
                        .content("{\"fullName\":\"Emp %s\",\"email\":\"%s\",\"password\":\"password\",\"role\":\"EMPLOYEE\"}"
                                .formatted(slug, email)))
                .andExpect(status().isCreated()).andReturn();
        UUID id = UUID.fromString(om.readTree(res.getResponse().getContentAsString()).get("id").asText());
        return new Staff(owner, login(email, "password"), id);
    }

    private ResultActions clockIn(String token) throws Exception {
        return mvc.perform(post("/api/attendance/clock-in").header("Authorization", bearer(token)));
    }

    private ResultActions clockIn(String token, double lat, double lng) throws Exception {
        return mvc.perform(post("/api/attendance/clock-in").header("Authorization", bearer(token))
                .contentType("application/json").content("{\"latitude\":%s,\"longitude\":%s}".formatted(lat, lng)));
    }

    private ResultActions clockOut(String token) throws Exception {
        return mvc.perform(post("/api/attendance/clock-out").header("Authorization", bearer(token)));
    }

    private AttendanceRecord todayRecord(UUID employeeId) {
        return attendance.findByEmployeeIdAndWorkDate(employeeId, LocalDate.now(KL)).orElseThrow();
    }

    @Test
    void inOutInOut_workedIsTotalMinusBreak() throws Exception {
        Staff s = staff("net");

        // First clock-in: nothing to report yet.
        clockIn(s.token()).andExpect(status().isOk())
                .andExpect(jsonPath("$.breakMinutes").value(0))
                .andExpect(jsonPath("$.clockOutAt").value(nullValue()));

        // Pretend they clocked in 180 minutes ago.
        AttendanceRecord r = todayRecord(s.id());
        Instant firstIn = Instant.now().minus(180, ChronoUnit.MINUTES).truncatedTo(ChronoUnit.MILLIS);
        r.setClockInAt(firstIn);
        attendance.save(r);
        AttendanceStatus firstStatus = r.getStatus();

        clockOut(s.token()).andExpect(status().isOk())
                .andExpect(jsonPath("$.workedMinutes").value(180))
                .andExpect(jsonPath("$.breakMinutes").value(0))
                .andExpect(jsonPath("$.clockOutAt").value(notNullValue()));

        // Pretend that clock-out was 60 minutes ago → a 60 minute break.
        r = todayRecord(s.id());
        r.setClockOutAt(Instant.now().minus(60, ChronoUnit.MINUTES));
        attendance.save(r);

        // Clock in again: same record resumed, original clock-in + status kept.
        JsonNode resumed = om.readTree(clockIn(s.token()).andExpect(status().isOk())
                .andExpect(jsonPath("$.breakMinutes").value(60))
                .andExpect(jsonPath("$.clockOutAt").value(nullValue()))
                .andExpect(jsonPath("$.workedMinutes").value(nullValue()))
                .andExpect(jsonPath("$.status").value(firstStatus.name()))
                .andReturn().getResponse().getContentAsString());
        assertEquals(firstIn, Instant.parse(resumed.get("clockInAt").asText()));

        // GET today agrees.
        mvc.perform(get("/api/attendance/today").header("Authorization", bearer(s.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.breakMinutes").value(60))
                .andExpect(jsonPath("$.clockOutAt").value(nullValue()));

        // Second clock-out: 180 min since first clock-in, minus the 60 min break.
        clockOut(s.token()).andExpect(status().isOk())
                .andExpect(jsonPath("$.workedMinutes").value(120))
                .andExpect(jsonPath("$.breakMinutes").value(60))
                .andExpect(jsonPath("$.clockOutAt").value(notNullValue()));

        // Still one record for the day, and history reports the net figure.
        assertEquals(120, todayRecord(s.id()).getWorkedMinutes());
        mvc.perform(get("/api/attendance/me").header("Authorization", bearer(s.token())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.days[0].workedLabel").value("2h 0m"));
    }

    @Test
    void breaksAccumulate_andWorkedNeverGoesNegative() throws Exception {
        Staff s = staff("acc");
        clockIn(s.token()).andExpect(status().isOk());
        clockOut(s.token()).andExpect(status().isOk());

        AttendanceRecord r = todayRecord(s.id());
        r.setClockInAt(Instant.now().minus(100, ChronoUnit.MINUTES));
        r.setClockOutAt(Instant.now().minus(30, ChronoUnit.MINUTES));
        attendance.save(r);
        clockIn(s.token()).andExpect(status().isOk()).andExpect(jsonPath("$.breakMinutes").value(30));
        clockOut(s.token()).andExpect(status().isOk()).andExpect(jsonPath("$.workedMinutes").value(70));

        r = todayRecord(s.id());
        r.setClockOutAt(Instant.now().minus(20, ChronoUnit.MINUTES));
        attendance.save(r);
        clockIn(s.token()).andExpect(status().isOk()).andExpect(jsonPath("$.breakMinutes").value(50));

        // A break longer than the span (bad data) clamps to zero rather than going negative.
        r = todayRecord(s.id());
        r.setBreakMinutes(10_000);
        attendance.save(r);
        clockOut(s.token()).andExpect(status().isOk()).andExpect(jsonPath("$.workedMinutes").value(0));
    }

    @Test
    void secondClockInWhileStillIn_400() throws Exception {
        Staff s = staff("dup");
        clockIn(s.token()).andExpect(status().isOk());
        clockIn(s.token()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Already clocked in today"));

        // Same after resuming.
        clockOut(s.token()).andExpect(status().isOk());
        clockIn(s.token()).andExpect(status().isOk());
        clockIn(s.token()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Already clocked in today"));
    }

    @Test
    void clockInAndOut_unauthenticated_401() throws Exception {
        mvc.perform(post("/api/attendance/clock-in")).andExpect(status().isUnauthorized());
        mvc.perform(post("/api/attendance/clock-out")).andExpect(status().isUnauthorized());
    }

    @Test
    void geofenceEnforcedOnResume() throws Exception {
        Staff s = staff("geo");
        var site = mvc.perform(post("/api/admin/work-locations").header("Authorization", bearer(s.ownerToken()))
                        .contentType("application/json")
                        .content("{\"name\":\"Resume Shop\",\"latitude\":%s,\"longitude\":%s,\"active\":true}"
                                .formatted(SITE_LAT, SITE_LNG)))
                .andExpect(status().isCreated()).andReturn();
        String siteId = om.readTree(site.getResponse().getContentAsString()).get("id").asText();
        mvc.perform(patch("/api/employees/" + s.id()).header("Authorization", bearer(s.ownerToken()))
                        .contentType("application/json").content("{\"workLocationId\":\"" + siteId + "\"}"))
                .andExpect(status().isOk());

        clockIn(s.token(), SITE_LAT, SITE_LNG).andExpect(status().isOk());
        clockOut(s.token()).andExpect(status().isOk());

        // Clocking in again from far away, or with no coordinates, is rejected…
        clockIn(s.token(), SITE_LAT + 0.05, SITE_LNG + 0.05).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Move within 100 m")));
        clockIn(s.token()).andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("Location required")));
        // …and the day stays clocked out.
        mvc.perform(get("/api/attendance/today").header("Authorization", bearer(s.token())))
                .andExpect(jsonPath("$.clockOutAt").value(notNullValue()));

        // From inside the fence it resumes.
        clockIn(s.token(), SITE_LAT, SITE_LNG).andExpect(status().isOk())
                .andExpect(jsonPath("$.clockOutAt").value(nullValue()))
                .andExpect(jsonPath("$.location").value("Resume Shop"));
    }
}
