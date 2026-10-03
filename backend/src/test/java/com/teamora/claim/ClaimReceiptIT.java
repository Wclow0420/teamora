package com.teamora.claim;

import com.fasterxml.jackson.databind.JsonNode;
import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Claim receipt photos. Each scenario builds an isolated company: an owner, an
 * HR admin, two managers, employee A (reports to manager 1) and employee B (no
 * manager → routes to the owner).
 */
class ClaimReceiptIT extends AbstractIntegrationTest {

    // 4 raw bytes (FF D8 FF D9) — a minimal JPEG SOI/EOI marker pair.
    private static final String SMALL_JPEG_B64 = "/9j/2Q==";
    private static final byte[] SMALL_JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xD9};
    // The 8-byte PNG signature (89 50 4E 47 0D 0A 1A 0A).
    private static final String SMALL_PNG_B64 = "iVBORw0KGgo=";
    private static final byte[] SMALL_PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};

    private record Co(String owner, String hr, String mgr, String otherMgr, String a, String b) {}

    private String register(String slug) throws Exception {
        String body = """
                {"companyName":"Rcpt %s","fullName":"Owner %s","email":"rc-owner-%s@example.com","password":"password"}"""
                .formatted(slug, slug, slug);
        var res = mvc.perform(post("/api/auth/register").contentType("application/json").content(body))
                .andExpect(status().isOk()).andReturn();
        return om.readTree(res.getResponse().getContentAsString()).get("accessToken").asText();
    }

    private String createEmp(String ownerToken, String email, String role, String managerId) throws Exception {
        String mgr = managerId == null ? "" : ",\"reportingManagerId\":\"" + managerId + "\"";
        String body = "{\"fullName\":\"%s\",\"email\":\"%s\",\"password\":\"password\",\"role\":\"%s\"%s}"
                .formatted(email, email, role, mgr);
        var res = mvc.perform(post("/api/employees").header("Authorization", bearer(ownerToken))
                        .contentType("application/json").content(body))
                .andExpect(status().isCreated()).andReturn();
        return om.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private Co setup() throws Exception {
        String slug = "c" + System.nanoTime();
        String owner = register(slug);
        createEmp(owner, "rc-hr-" + slug + "@example.com", "HR_ADMIN", null);
        String mgrId = createEmp(owner, "rc-mgr-" + slug + "@example.com", "MANAGER", null);
        createEmp(owner, "rc-mgr2-" + slug + "@example.com", "MANAGER", null);
        createEmp(owner, "rc-a-" + slug + "@example.com", "EMPLOYEE", mgrId);
        createEmp(owner, "rc-b-" + slug + "@example.com", "EMPLOYEE", null);
        return new Co(owner,
                login("rc-hr-" + slug + "@example.com", "password"),
                login("rc-mgr-" + slug + "@example.com", "password"),
                login("rc-mgr2-" + slug + "@example.com", "password"),
                login("rc-a-" + slug + "@example.com", "password"),
                login("rc-b-" + slug + "@example.com", "password"));
    }

    private org.springframework.test.web.servlet.ResultActions submit(String token, String receiptBase64) throws Exception {
        String receipt = receiptBase64 == null ? "" : ",\"receiptBase64\":\"" + receiptBase64 + "\"";
        String body = "{\"category\":\"MEAL\",\"title\":\"Team lunch\",\"amount\":42.50,\"claimDate\":\"2026-09-15\"%s}"
                .formatted(receipt);
        return mvc.perform(post("/api/claims").header("Authorization", bearer(token))
                .contentType("application/json").content(body));
    }

    private String submitOk(String token, String receiptBase64, boolean expectReceipt) throws Exception {
        var res = submit(token, receiptBase64)
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasReceipt").value(expectReceipt))
                .andReturn();
        return om.readTree(res.getResponse().getContentAsString()).get("id").asText();
    }

    private org.springframework.test.web.servlet.ResultActions receipt(String token, String claimId) throws Exception {
        return mvc.perform(get("/api/claims/" + claimId + "/receipt").header("Authorization", bearer(token)));
    }

    private JsonNode findById(JsonNode array, String id) {
        for (JsonNode n : array) {
            if (id.equals(n.get("id").asText())) return n;
        }
        throw new AssertionError("Claim " + id + " not in list");
    }

    @Test
    void submitWithReceipt_flagsHasReceipt_onListAndPendingQueue() throws Exception {
        Co co = setup();
        String withReceipt = submitOk(co.a(), SMALL_JPEG_B64, true);
        String without = submitOk(co.a(), null, false);

        var mine = mvc.perform(get("/api/claims").header("Authorization", bearer(co.a())))
                .andExpect(status().isOk()).andReturn();
        JsonNode claims = om.readTree(mine.getResponse().getContentAsString()).get("claims");
        assertEquals(true, findById(claims, withReceipt).get("hasReceipt").asBoolean());
        assertEquals(false, findById(claims, without).get("hasReceipt").asBoolean());

        var pending = mvc.perform(get("/api/admin/claims").header("Authorization", bearer(co.mgr())))
                .andExpect(status().isOk()).andReturn();
        JsonNode queue = om.readTree(pending.getResponse().getContentAsString());
        assertEquals(true, findById(queue, withReceipt).get("hasReceipt").asBoolean());
        assertEquals(false, findById(queue, without).get("hasReceipt").asBoolean());
    }

    @Test
    void ownerOfClaim_andApprovers_canFetchReceipt() throws Exception {
        Co co = setup();
        String id = submitOk(co.a(), SMALL_JPEG_B64, true);

        var res = receipt(co.a(), id)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("image/jpeg")))
                .andReturn();
        assertArrayEquals(SMALL_JPEG, res.getResponse().getContentAsByteArray());

        // The reporting manager, HR admin and company owner may all decide it → may view it.
        assertArrayEquals(SMALL_JPEG, receipt(co.mgr(), id).andExpect(status().isOk())
                .andReturn().getResponse().getContentAsByteArray());
        receipt(co.hr(), id).andExpect(status().isOk());
        receipt(co.owner(), id).andExpect(status().isOk());

        // Still viewable after the decision.
        mvc.perform(post("/api/admin/claims/" + id + "/approve").header("Authorization", bearer(co.mgr())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.hasReceipt").value(true));
        receipt(co.a(), id).andExpect(status().isOk());
        receipt(co.mgr(), id).andExpect(status().isOk());
    }

    @Test
    void dataUrlPrefixStripped_andTypeInferred() throws Exception {
        Co co = setup();
        String id = submitOk(co.a(), "data:image/png;base64," + SMALL_PNG_B64, true);
        var res = receipt(co.a(), id)
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("image/png")))
                .andReturn();
        assertArrayEquals(SMALL_PNG, res.getResponse().getContentAsByteArray());
    }

    @Test
    void typeComesFromTheBytes_notTheDeclaredDataUrl_andNonImagesAreRefused() throws Exception {
        Co co = setup();
        // JPEG bytes labelled as PNG → stored and served as what they really are.
        String id = submitOk(co.a(), "data:image/png;base64," + SMALL_JPEG_B64, true);
        receipt(co.a(), id).andExpect(header().string("Content-Type", containsString("image/jpeg")));
        // A GIF (not on the whitelist), plain text, and a non-image data URL are 400.
        submit(co.a(), "R0lGODlhAQABAAAAACw=").andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("JPEG, PNG or HEIC")));
        submit(co.a(), "aGVsbG8gd29ybGQ=").andExpect(status().isBadRequest());
        submit(co.a(), "data:text/html;base64," + SMALL_JPEG_B64).andExpect(status().isBadRequest());
    }

    @Test
    void unrelatedEmployee_andNonApprovingManager_forbidden() throws Exception {
        Co co = setup();
        String aClaim = submitOk(co.a(), SMALL_JPEG_B64, true);
        String bClaim = submitOk(co.b(), SMALL_JPEG_B64, true);

        // A colleague who is neither the owner nor an approver.
        receipt(co.b(), aClaim).andExpect(status().isForbidden());
        // A manager the claim does not route to (A reports to mgr; B routes to the owner).
        receipt(co.otherMgr(), aClaim).andExpect(status().isForbidden());
        receipt(co.mgr(), bClaim).andExpect(status().isForbidden());
        // …while the owner (B's approver) can.
        receipt(co.owner(), bClaim).andExpect(status().isOk());
    }

    @Test
    void otherCompany_cannotFetch() throws Exception {
        Co co = setup();
        String id = submitOk(co.a(), SMALL_JPEG_B64, true);
        Co other = setup();

        // Cross-tenant → 404 (existence is not leaked), for an admin and an employee alike.
        receipt(other.owner(), id).andExpect(status().isNotFound());
        receipt(other.a(), id).andExpect(status().isNotFound());
    }

    @Test
    void unauthenticated_401() throws Exception {
        Co co = setup();
        String id = submitOk(co.a(), SMALL_JPEG_B64, true);
        mvc.perform(get("/api/claims/" + id + "/receipt")).andExpect(status().isUnauthorized());
    }

    @Test
    void oversizedOrInvalidReceipt_400_andNoClaimCreated() throws Exception {
        Co co = setup();
        // ~2.25 MB decoded (3 MB of base64) → over the 2 MB photo cap, under the 3.5 MB body cap.
        submit(co.a(), "A".repeat(3_000_000))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value(containsString("too large")));
        // Over the 3.5 MB request-body cap → refused before it's read.
        submit(co.a(), "A".repeat(4_200_000))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.message").value(containsString("too large")));
        submit(co.a(), "not*base64!").andExpect(status().isBadRequest());

        mvc.perform(get("/api/claims").header("Authorization", bearer(co.a())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.claims.length()").value(0));
    }

    @Test
    void submitWithoutReceipt_stillWorks_andReceiptIs404() throws Exception {
        Co co = setup();
        String id = submitOk(co.a(), null, false);
        receipt(co.a(), id).andExpect(status().isNotFound());
        receipt(co.mgr(), id).andExpect(status().isNotFound());
        // Unknown claim id → 404.
        receipt(co.a(), java.util.UUID.randomUUID().toString()).andExpect(status().isNotFound());
    }
}
