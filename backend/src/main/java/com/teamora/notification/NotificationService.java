package com.teamora.notification;

import com.teamora.employee.Employee;
import com.teamora.notification.dto.NotificationDtos.NotificationItem;
import com.teamora.notification.dto.NotificationDtos.NotificationListResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    /** Column limits on {@code notifications.title} / {@code .body}. */
    private static final int TITLE_MAX = 160;
    private static final int BODY_MAX = 500;

    private final NotificationRepository notifications;
    private final PushTokenRepository pushTokens;
    private final ExpoPushService expoPush;

    /** The employee's notifications split into Today / Earlier, newest first, + unread count. */
    public NotificationListResponse list(Employee employee) {
        Instant now = Instant.now();
        LocalDate today = LocalDate.now(ZoneId.systemDefault());

        List<NotificationItem> todayItems = new ArrayList<>();
        List<NotificationItem> earlierItems = new ArrayList<>();
        for (Notification n : notifications.findByEmployeeIdOrderByOccurredAtDesc(employee.getId())) {
            LocalDate day = n.getOccurredAt().atZone(ZoneId.systemDefault()).toLocalDate();
            NotificationItem item = NotificationItem.from(n, now);
            if (day.isEqual(today)) {
                todayItems.add(item);
            } else {
                earlierItems.add(item);
            }
        }
        int unreadCount = notifications.countByEmployeeIdAndReadFalse(employee.getId());
        return new NotificationListResponse(todayItems, earlierItems, unreadCount);
    }

    @Transactional
    public void markAllRead(Employee employee) {
        notifications.markAllRead(employee.getId());
    }

    /** Create an in-app notification and fan it out to the employee's devices via push. */
    @Transactional
    public void create(Employee employee, NotificationType type, String title, String body) {
        // Bodies interpolate user text (claim titles, decline reasons) — never let that overflow the columns.
        title = clip(title, TITLE_MAX);
        body = clip(body, BODY_MAX);
        Notification n = Notification.builder()
                .employee(employee)
                .type(type)
                .title(title)
                .body(body)
                .read(false)
                .occurredAt(Instant.now())
                .build();
        n.setCompany(employee.getCompany());
        notifications.save(n);
        // Best-effort push (no-op when the employee has no registered devices).
        expoPush.sendToEmployee(employee.getId(), title, body, type.name());
    }

    /** Register (upsert) an Expo push token for one of the employee's devices. */
    @Transactional
    public void registerPushToken(Employee employee, String token, String platform) {
        PushToken existing = pushTokens.findByToken(token).orElse(null);
        if (existing != null) {
            existing.setEmployee(employee);
            existing.setCompany(employee.getCompany());
            existing.setPlatform(platform);
            return;
        }
        PushToken pt = PushToken.builder().employee(employee).token(token).platform(platform).build();
        pt.setCompany(employee.getCompany());
        pushTokens.save(pt);
    }

    /** Unregister one of the caller's own devices; someone else's token is left alone (still 204). */
    @Transactional
    public void removePushToken(Employee employee, String token) {
        pushTokens.deleteByTokenAndEmployeeId(token, employee.getId());
    }

    private static String clip(String s, int max) {
        if (s == null || s.length() <= max) {
            return s;
        }
        return s.substring(0, max - 1) + "\u2026";
    }
}
