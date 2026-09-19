package com.teamora.notification;

import com.teamora.config.TeamoraProperties;
import com.teamora.employee.Employee;
import com.teamora.employee.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;

/** Demo notifications (back-dated occurred_at) so the staff feed shows Today + Earlier. */
@Slf4j
@Component
@Order(9)
@RequiredArgsConstructor
public class NotificationSeeder implements CommandLineRunner {

    private final NotificationRepository notifications;
    private final EmployeeRepository employees;
    private final TeamoraProperties props;

    @Override
    @Transactional
    public void run(String... args) {
        if (!props.seed() || notifications.count() > 0) {
            return;
        }
        employees.findByEmailIgnoreCase("amir@lumi.com").ifPresent(amir -> {
            Instant now = Instant.now();
            notifications.save(notif(amir, NotificationType.LEAVE_APPROVED,
                    "Leave approved",
                    "Your annual leave (18–19 Jun) was approved by Sarah.",
                    now.minus(Duration.ofHours(2)), false));
            notifications.save(notif(amir, NotificationType.PAYSLIP_READY,
                    "Payslip is ready",
                    "June payslip · RM 4,285.50 net. Tap to view.",
                    now.minus(Duration.ofHours(5)), false));
            notifications.save(notif(amir, NotificationType.SHIFT_REMINDER,
                    "Shift reminder",
                    "Morning shift tomorrow · 9:00 AM at Bangsar South HQ.",
                    now.minus(Duration.ofHours(9)), true));
            notifications.save(notif(amir, NotificationType.CLAIM_APPROVED,
                    "Claim approved",
                    "Travel claim RM 48.00 reimbursed to your account.",
                    now.minus(Duration.ofHours(28)), true));
            notifications.save(notif(amir, NotificationType.CLOCK_OUT_REMINDER,
                    "Clock-out reminder",
                    "You forgot to clock out on 11 Jun. Tap to fix.",
                    now.minus(Duration.ofDays(2)), true));
            log.info("Seeded 5 notifications for amir@lumi.com");
        });
    }

    private Notification notif(Employee e, NotificationType type, String title, String body,
                              Instant occurredAt, boolean read) {
        Notification n = Notification.builder()
                .employee(e)
                .type(type)
                .title(title)
                .body(body)
                .read(read)
                .occurredAt(occurredAt)
                .build();
        n.setCompany(e.getCompany());
        return n;
    }
}
