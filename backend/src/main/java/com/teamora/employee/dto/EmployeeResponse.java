package com.teamora.employee.dto;

import com.teamora.employee.Employee;
import com.teamora.employee.MaritalStatus;
import com.teamora.employee.Role;
import org.hibernate.Hibernate;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/** Public projection of an {@link Employee}. Never exposes the password hash. */
public record EmployeeResponse(
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
        BigDecimal monthlySalary,
        MaritalStatus maritalStatus,
        Boolean spouseWorking,
        int numChildren
) {
    /**
     * Safe in any context: only reads the reporting manager when it's already
     * initialized (e.g. fetch-joined), so it won't trip a LazyInitialization
     * outside a transaction (open-in-view is off). Use {@link #withManager} on
     * read paths that fetch-join the manager.
     */
    public static EmployeeResponse from(Employee e) {
        Employee mgr = (e.getReportingManager() != null && Hibernate.isInitialized(e.getReportingManager()))
                ? e.getReportingManager()
                : null;
        return build(e, mgr);
    }

    /** For paths that fetch-join {@code reportingManager} (list / get / after update). */
    public static EmployeeResponse withManager(Employee e) {
        return build(e, e.getReportingManager());
    }

    private static EmployeeResponse build(Employee e, Employee mgr) {
        return new EmployeeResponse(
                e.getId(), e.getEmail(), e.getFullName(), e.getInitial(), e.getRole(),
                e.getJobTitle(), e.getDepartment(), e.getLocation(), e.getStaffId(),
                e.getPhone(), e.getJoinDate(), e.isActive(),
                e.getCompany() != null ? e.getCompany().getId() : null,
                e.getCompany() != null ? e.getCompany().getName() : null,
                mgr != null ? mgr.getId() : null,
                mgr != null ? mgr.getFullName() : null,
                e.getMonthlySalary(),
                e.getMaritalStatus(),
                e.getSpouseWorking(),
                e.getNumChildren());
    }
}
