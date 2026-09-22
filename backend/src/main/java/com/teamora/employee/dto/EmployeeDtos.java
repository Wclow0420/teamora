package com.teamora.employee.dto;

import com.teamora.employee.MaritalStatus;
import com.teamora.employee.PayBasis;
import com.teamora.employee.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import java.math.BigDecimal;
import java.util.UUID;

/** Request payloads for employee management (admin). */
public final class EmployeeDtos {

    private EmployeeDtos() {}

    /** Add an employee to the caller's company. */
    public record CreateEmployeeRequest(
            @NotBlank String fullName,
            @Email @NotBlank String email,
            @NotBlank String password,
            @NotNull Role role,
            String jobTitle,
            String department,
            String staffId,
            String phone,
            UUID reportingManagerId,
            UUID workLocationId,
            @PositiveOrZero BigDecimal monthlySalary,
            MaritalStatus maritalStatus,
            Boolean spouseWorking,
            @PositiveOrZero Integer numChildren,
            PayBasis payBasis,
            @Min(0) @Max(127) Integer workingDays,
            @PositiveOrZero BigDecimal hoursPerDay,
            // Statutory & bank identity (all optional)
            String nric,
            String epfNo,
            String socsoNo,
            String taxNo,
            String bankName,
            String bankAccountNo
    ) {}

    public record ChangeRoleRequest(@NotNull Role role) {}

    /** Partial update of an employee's profile (and optionally role). */
    public record UpdateEmployeeRequest(
            String fullName,
            String jobTitle,
            String department,
            String phone,
            String staffId,
            Role role,
            UUID reportingManagerId,
            UUID workLocationId,
            @PositiveOrZero BigDecimal monthlySalary,
            MaritalStatus maritalStatus,
            Boolean spouseWorking,
            @PositiveOrZero Integer numChildren,
            PayBasis payBasis,
            @Min(0) @Max(127) Integer workingDays,
            @PositiveOrZero BigDecimal hoursPerDay,
            // Statutory & bank identity (all optional)
            String nric,
            String epfNo,
            String socsoNo,
            String taxNo,
            String bankName,
            String bankAccountNo
    ) {}

    /** Lightweight option for the "reporting manager" picker. */
    public record ManagerOption(UUID id, String fullName, Role role, String jobTitle) {
        public static ManagerOption from(com.teamora.employee.Employee e) {
            return new ManagerOption(e.getId(), e.getFullName(), e.getRole(), e.getJobTitle());
        }
    }
}
