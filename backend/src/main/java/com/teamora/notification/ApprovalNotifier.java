package com.teamora.notification;

import com.teamora.employee.Employee;
import com.teamora.employee.EmployeeRepository;
import com.teamora.employee.Role;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Notification helpers for the approval flows (leave / claims / overtime), so
 * each domain just calls one method. Requests notify the resolved approver
 * (reporting manager → company owner fallback); decisions notify the requester.
 */
@Component
@RequiredArgsConstructor
public class ApprovalNotifier {

    private final NotificationService notifications;
    private final EmployeeRepository employees;

    /** Tell the requester their request was decided. */
    public void notifyRequester(Employee requester, NotificationType type, String title, String body) {
        notifications.create(requester, type, title, body);
    }

    /** Tell whoever approves this requester (their reporting manager, else the owner). */
    public void notifyApprover(UUID requesterId, UUID companyId, NotificationType type, String title, String body) {
        Employee requester = employees.findByIdAndCompanyIdWithManager(requesterId, companyId).orElse(null);
        if (requester == null) return;
        Employee approver = requester.getReportingManager() != null
                ? requester.getReportingManager()
                : employees.findByCompanyIdAndRole(companyId, Role.OWNER).orElse(null);
        if (approver == null || approver.getId().equals(requesterId)) return;
        notifications.create(approver, type, title, body);
    }
}
