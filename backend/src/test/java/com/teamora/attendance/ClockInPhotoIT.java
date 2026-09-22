package com.teamora.attendance;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Clock-in selfie storage + retrieval. Uses fresh companies/employees per
 * scenario (clock-in is once/day) so it never touches shared seeded attendance.
 *
 * Covers: clock-in with a valid base64 JPEG stores the photo (visible on the
 * live board); GET the photo as self / same-company admin → 200; other-company
 * user → 403; oversized → 400; no-photo clock-in still works and has no photo.
 */
class ClockInPhotoIT extends AbstractIntegrationTest {

    // 4 raw bytes (FF D8 FF D9) — a minimal JPEG SOI/EOI marker pair.
    private static final String SMALL_JPEG_B64 = "/9j/2Q==";
    private static final byte[] SMALL_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};

    private String owner;

    private String register(String slug) throws Exception {
        String body = """
                {"companyName":"Photo %s","fullName":"Owner %s","email":"photoowner-%s@example.com","password":"password"}"""
                .formatted(slug, slug, slug);
        var res = mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    /** Create an EMPLOYEE in the owner's company and return its login email. */
    private String createEmp(String ownerToken, String email) throws Exception {
        String body = "{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"password\",\"role\":\"EMPLOYEE\"}"
                .formatted(email, email);
        mvc.perform(post("/api/employees").header("Authorization", bearer(ownerToken))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated());
        return email;
    }

    /** The attendance record id for an employee on today's live board (asserts hasPhoto matches). */
    private String recordIdFromLiveBoard(String adminToken, String employeeEmail, boolean expectPhoto)
            throws Exception {
        var res = mvc.perform(get("/api/admin/attendance/live").header("Authorization", bearer(adminToken)))
                .andExpect(status().isOk()).andReturn();
        JsonNode staff = om.readTree(res.getResponse().getContentAsString()).get("staff");
        for (JsonNode row : staff) {
            // rows carry employeeId; match by the record having clocked in (hasPhoto/attendanceRecordId).
            if (row.hasNonNull("attendanceRecordId")) {
                // Find the one row that has a photo (only our clocked-in emp does in a fresh company).
                if (row.get("hasPhoto").asBoolean() == expectPhoto && expectPhoto) {
                    return row.get("attendanceRecordId").asText();
                }
            }
        }
        // Fallback: return the single clocked-in row's id regardless of photo flag.
        for (JsonNode row : staff) {
            if (row.hasNonNull("attendanceRecordId")) {
                assertEquals(expectPhoto, row.get("hasPhoto").asBoolean(),
                        "hasPhoto flag for " + employeeEmail);
                return row.get("attendanceRecordId").asText();
            }
        }
        throw new AssertionError("No clocked-in row on the live board");
    }

    private String clockInWithPhoto(String empToken, String photoBase64) throws Exception {
        String body = "{\"photoBase64\":\"%s\"}".formatted(photoBase64);
        var res = mvc.perform(post("/api/attendance/clock-in").header("Authorization", bearer(empToken))
                        .contentType("application/json").content(body))
                .andExpect(status().isOk())
                .andReturn();
        return res.getResponse().getContentAsString();
    }

    @Test
    void clockInStoresPhoto_visibleOnLiveBoard() throws Exception {
        owner = register("store" + System.nanoTime());
        String email = createEmp(owner, "ph-store-" + System.nanoTime() + "@example.com");
        String emp = login(email, "password");

        clockInWithPhoto(emp, SMALL_JPEG_B64);

        String recordId = recordIdFromLiveBoard(owner, email, true);
        assertNotNull(recordId);
    }

    @Test
    void getPhoto_asSelf_returnsBytesAndType() throws Exception {
        owner = register("self" + System.nanoTime());
        String email = createEmp(owner, "ph-self-" + System.nanoTime() + "@example.com");
        String emp = login(email, "password");
        clockInWithPhoto(emp, SMALL_JPEG_B64);
        String recordId = recordIdFromLiveBoard(owner, email, true);

        var res = mvc.perform(get("/api/attendance/records/" + recordId + "/photo")
                        .header("Authorization", bearer(emp)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("image/jpeg")))
                .andReturn();
        byte[] bytes = res.getResponse().getContentAsByteArray();
        assertEquals(SMALL_JPEG.length, bytes.length);
        assertTrue(java.util.Arrays.equals(SMALL_JPEG, bytes), "photo bytes round-trip");
    }

    @Test
    void getPhoto_asSameCompanyAdmin_ok() throws Exception {
        owner = register("admin" + System.nanoTime());
        String email = createEmp(owner, "ph-admin-" + System.nanoTime() + "@example.com");
        String emp = login(email, "password");
        clockInWithPhoto(emp, SMALL_JPEG_B64);
        String recordId = recordIdFromLiveBoard(owner, email, true);

        mvc.perform(get("/api/attendance/records/" + recordId + "/photo")
                        .header("Authorization", bearer(owner)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("image/jpeg")));
    }

    @Test
    void getPhoto_dataUrlPrefixStripped_andTypeInferred() throws Exception {
        owner = register("dataurl" + System.nanoTime());
        String email = createEmp(owner, "ph-du-" + System.nanoTime() + "@example.com");
        String emp = login(email, "password");
        // PNG data URL — service strips the prefix and infers image/png.
        clockInWithPhoto(emp, "data:image/png;base64," + SMALL_JPEG_B64);
        String recordId = recordIdFromLiveBoard(owner, email, true);

        mvc.perform(get("/api/attendance/records/" + recordId + "/photo")
                        .header("Authorization", bearer(emp)))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("image/png")));
    }

    @Test
    void getPhoto_asOtherCompanyUser_forbidden() throws Exception {
        owner = register("owner1-" + System.nanoTime());
        String email = createEmp(owner, "ph-o1-" + System.nanoTime() + "@example.com");
        String emp = login(email, "password");
        clockInWithPhoto(emp, SMALL_JPEG_B64);
        String recordId = recordIdFromLiveBoard(owner, email, true);

        // A DIFFERENT company's owner (admin, but wrong tenant) → 403.
        String otherOwner = register("owner2-" + System.nanoTime());
        mvc.perform(get("/api/attendance/records/" + recordId + "/photo")
                        .header("Authorization", bearer(otherOwner)))
                .andExpect(status().isForbidden());

        // A DIFFERENT company's employee → 403.
        String otherEmail = createEmp(otherOwner, "ph-o2-" + System.nanoTime() + "@example.com");
        String otherEmp = login(otherEmail, "password");
        mvc.perform(get("/api/attendance/records/" + recordId + "/photo")
                        .header("Authorization", bearer(otherEmp)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getPhoto_unauthenticated_returns401() throws Exception {
        owner = register("unauth" + System.nanoTime());
        String email = createEmp(owner, "ph-un-" + System.nanoTime() + "@example.com");
        String emp = login(email, "password");
        clockInWithPhoto(emp, SMALL_JPEG_B64);
        String recordId = recordIdFromLiveBoard(owner, email, true);

        mvc.perform(get("/api/attendance/records/" + recordId + "/photo"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void oversizedPhoto_rejectedWith400() throws Exception {
        owner = register("big" + System.nanoTime());
        String email = createEmp(owner, "ph-big-" + System.nanoTime() + "@example.com");
        String emp = login(email, "password");

        // ~3 MB of base64 'A' → well over the 2 MB decoded cap.
        String big = "A".repeat(4_200_000);
        String body = "{\"photoBase64\":\"%s\"}".formatted(big);
        mvc.perform(post("/api/attendance/clock-in").header("Authorization", bearer(emp))
                        .contentType("application/json").content(body))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("too large")));
    }

    @Test
    void clockInWithoutPhoto_stillWorks_andHasNoPhoto() throws Exception {
        owner = register("nophoto" + System.nanoTime());
        String email = createEmp(owner, "ph-no-" + System.nanoTime() + "@example.com");
        String emp = login(email, "password");

        // No body → clocks in (unassigned employee, no geofence).
        mvc.perform(post("/api/attendance/clock-in").header("Authorization", bearer(emp)))
                .andExpect(status().isOk());

        String recordId = recordIdFromLiveBoard(owner, email, false);

        // No stored photo → 404 on retrieval (record exists, photo doesn't).
        mvc.perform(get("/api/attendance/records/" + recordId + "/photo")
                        .header("Authorization", bearer(emp)))
                .andExpect(status().isNotFound());
    }
}
