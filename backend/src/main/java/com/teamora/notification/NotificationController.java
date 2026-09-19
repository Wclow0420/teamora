package com.teamora.notification;

import com.teamora.notification.dto.NotificationDtos.NotificationListResponse;
import com.teamora.notification.dto.NotificationDtos.PushTokenRequest;
import com.teamora.security.CurrentEmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final CurrentEmployeeService currentEmployee;

    @GetMapping("/api/notifications")
    public NotificationListResponse list() {
        return notificationService.list(currentEmployee.require());
    }

    @PostMapping("/api/notifications/read-all")
    public ResponseEntity<Void> markAllRead() {
        notificationService.markAllRead(currentEmployee.require());
        return ResponseEntity.noContent().build();
    }

    /** Register this device's Expo push token. */
    @PostMapping("/api/notifications/push-token")
    public ResponseEntity<Void> registerPushToken(@Valid @RequestBody PushTokenRequest req) {
        notificationService.registerPushToken(currentEmployee.require(), req.token(), req.platform());
        return ResponseEntity.noContent().build();
    }

    /** Unregister a push token (e.g. on sign-out). */
    @DeleteMapping("/api/notifications/push-token")
    public ResponseEntity<Void> removePushToken(@RequestParam String token) {
        notificationService.removePushToken(token);
        return ResponseEntity.noContent().build();
    }
}
