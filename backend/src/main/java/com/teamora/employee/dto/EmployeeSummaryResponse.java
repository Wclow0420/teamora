package com.teamora.employee.dto;

import com.teamora.employee.Employee;
import com.teamora.employee.Role;
import org.hibernate.Hibernate;

import java.time.LocalDate;
import java.util.UUID;

/**
 * Directory-list projection of an {@link Employee} ({@code GET /api/employees}).
 * Deliberately slim: no NRIC / EPF / SOCSO / tax numbers, bank details, salary,
 * tax profile or pay settings — those are only on the detail endpoint, and only
 * for OWNER/HR_ADMIN. Inactive people are listed too ({@code active=false}) so an
 * admin can find and reactivate them.
 */
public record EmployeeSummaryResponse(
        UUID id,
        String email,
        String fullName,
        String initial,
        Role role,
        String jobTitle,
        String department,
        String location,
        String staffId,
        String phone,
        LocalDate joinDate,
        boolean active,
        UUID companyId,
        String companyName,
        UUID reportingManagerId,
        String reportingManagerName,
        UUID workLocationId,
        String workLocationName
) {
    /** For list queries that fetch-join the company, reporting manager and work location. */
    public static EmployeeSummaryResponse from(Employee e) {
        Employee mgr = (e.getReportingManager() != null && Hibernate.isInitialized(e.getReportingManager()))
                ? e.getReportingManager() : null;
        var wl = (e.getWorkLocation() != null && Hibernate.isInitialized(e.getWorkLocation()))
                ? e.getWorkLocation() : null;
        return new EmployeeSummaryResponse(
                e.getId(), e.getEmail(), e.getFullName(), e.getInitial(), e.getRole(),
                e.getJobTitle(), e.getDepartment(), wl != null ? wl.getName() : null, e.getStaffId(),
                e.getPhone(), e.getJoinDate(), e.isActive(),
                e.getCompany() != null ? e.getCompany().getId() : null,
                e.getCompany() != null ? e.getCompany().getName() : null,
                mgr != null ? mgr.getId() : null,
                mgr != null ? mgr.getFullName() : null,
                wl != null ? wl.getId() : null,
                wl != null ? wl.getName() : null);
    }
}
