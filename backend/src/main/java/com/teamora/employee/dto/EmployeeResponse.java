package com.teamora.employee.dto;

import com.teamora.employee.Employee;
import com.teamora.employee.MaritalStatus;
import com.teamora.employee.PayBasis;
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
        UUID workLocationId,
        String workLocationName,
        BigDecimal monthlySalary,
        MaritalStatus maritalStatus,
        Boolean spouseWorking,
        int numChildren,
        // Statutory & bank identity (all optional)
        String nric,
        String epfNo,
        String socsoNo,
        String taxNo,
        String bankName,
        String bankAccountNo,
        // Effective compensation/schedule (resolved against the company default) +
        // indicative derived rates for the current month. Null on list/me paths that
        // don't compute them (see withComp).
        PayBasis payBasis,
        Integer workingDays,
        BigDecimal hoursPerDay,
        BigDecimal derivedDailyRate,
        BigDecimal derivedHourlyRate
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
        return build(e, mgr, null, null, null, null, null);
    }

    /** For paths that fetch-join {@code reportingManager} (list / after update). */
    public static EmployeeResponse withManager(Employee e) {
        return build(e, e.getReportingManager(), null, null, null, null, null);
    }

    /** Detail path: adds the effective schedule + indicative derived daily/hourly rates. */
    public static EmployeeResponse withComp(Employee e, PayBasis payBasis, Integer workingDays,
                                            BigDecimal hoursPerDay, BigDecimal derivedDailyRate,
                                            BigDecimal derivedHourlyRate) {
        return build(e, e.getReportingManager(), payBasis, workingDays, hoursPerDay,
                derivedDailyRate, derivedHourlyRate);
    }

    private static EmployeeResponse build(Employee e, Employee mgr, PayBasis payBasis, Integer workingDays,
                                          BigDecimal hoursPerDay, BigDecimal derivedDailyRate,
                                          BigDecimal derivedHourlyRate) {
        // Only read the work location when it's already initialized (fetch-joined),
        // so this stays safe outside a transaction (open-in-view is off).
        var wl = (e.getWorkLocation() != null && Hibernate.isInitialized(e.getWorkLocation()))
                ? e.getWorkLocation()
                : null;
        return new EmployeeResponse(
                e.getId(), e.getEmail(), e.getFullName(), e.getInitial(), e.getRole(),
                e.getJobTitle(), e.getDepartment(), e.getLocation(), e.getStaffId(),
                e.getPhone(), e.getJoinDate(), e.isActive(),
                e.getCompany() != null ? e.getCompany().getId() : null,
                e.getCompany() != null ? e.getCompany().getName() : null,
                mgr != null ? mgr.getId() : null,
                mgr != null ? mgr.getFullName() : null,
                wl != null ? wl.getId() : null,
                wl != null ? wl.getName() : null,
                e.getMonthlySalary(),
                e.getMaritalStatus(),
                e.getSpouseWorking(),
                e.getNumChildren(),
                e.getNric(), e.getEpfNo(), e.getSocsoNo(), e.getTaxNo(),
                e.getBankName(), e.getBankAccountNo(),
                payBasis, workingDays, hoursPerDay, derivedDailyRate, derivedHourlyRate);
    }
}
