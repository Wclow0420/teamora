package com.teamora.notification;

import com.teamora.AbstractIntegrationTest;
import org.junit.jupiter.api.Test;

import static org.hamcrest.Matchers.greaterThan;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/** Notifications feed: list (seeded), mark-all-read, and unread count goes to zero. */
class NotificationIT extends AbstractIntegrationTest {

    @Test
    void list_markAllRead_thenUnreadIsZero() throws Exception {
        String token = login("amir@lumi.com", "password");

        // Seeded notifications: some items present, unread > 0.
        mvc.perform(get("/api/notifications").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount", greaterThan(0)))
                .andExpect(jsonPath("$.today").isArray())
                .andExpect(jsonPath("$.earlier").isArray());

        // Mark all read → 204.
        mvc.perform(post("/api/notifications/read-all").header("Authorization", bearer(token)))
                .andExpect(status().isNoContent());

        // Now unread count is zero.
        mvc.perform(get("/api/notifications").header("Authorization", bearer(token)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.unreadCount").value(0));
    }

    @Test
    void unauthenticated_is401() throws Exception {
        mvc.perform(get("/api/notifications"))
                .andExpect(status().isUnauthorized());
    }
}
