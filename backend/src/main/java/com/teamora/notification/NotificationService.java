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

    @Transactional
    public void removePushToken(String token) {
        pushTokens.deleteByToken(token);
    }
}
