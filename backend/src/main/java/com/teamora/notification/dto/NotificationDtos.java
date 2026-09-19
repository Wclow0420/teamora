package com.teamora.notification.dto;

import com.teamora.notification.Notification;
import jakarta.validation.constraints.NotBlank;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

public final class NotificationDtos {

    private NotificationDtos() {}

    /** Register/unregister an Expo push token for the current device. */
    public record PushTokenRequest(@NotBlank String token, String platform) {}

    /** A single feed row: icon tile + title/body + relative time + unread dot. */
    public record NotificationItem(
            UUID id,
            String iconName,
            String title,
            String body,
            String timeLabel,
            String accentColorKey,
            boolean unread
    ) {
        public static NotificationItem from(Notification n, Instant now) {
            return new NotificationItem(
                    n.getId(),
                    n.getType().iconName(),
                    n.getTitle(),
                    n.getBody(),
                    relativeLabel(n.getOccurredAt(), now),
                    n.getType().accentColorKey(),
                    !n.isRead());
        }
    }

    /** The two-section feed + an unread badge count. */
    public record NotificationListResponse(
            List<NotificationItem> today,
            List<NotificationItem> earlier,
            int unreadCount
    ) {}

    static String relativeLabel(Instant occurredAt, Instant now) {
        Duration d = Duration.between(occurredAt, now);
        if (d.isNegative()) {
            return "Just now";
        }
        long minutes = d.toMinutes();
        if (minutes < 60) {
            return minutes + "m ago";
        }
        long hours = d.toHours();
        if (hours < 24) {
            return hours + "h ago";
        }
        long days = d.toDays();
        if (days == 1) {
            return "Yesterday";
        }
        return days + " days ago";
    }
}
