package com.teamora.employee.dto;

import jakarta.validation.constraints.DecimalMax;
import com.teamora.employee.MaritalStatus;
import com.teamora.employee.PayBasis;
import com.teamora.employee.Role;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.math.BigDecimal;
import java.util.UUID;

/** Request payloads for employee management (admin). */
public final class EmployeeDtos {

    private EmployeeDtos() {}

    /** Fits NUMERIC(12,2) with a wide margin; anything above is a typo. */
    public static final String MAX_SALARY = "1000000";

    /** Add an employee to the caller's company. */
    public record CreateEmployeeRequest(
            @NotBlank @Size(max = 100, message = "Name must be at most 100 characters") String fullName,
            @Email @NotBlank @Size(max = 254, message = "Email must be at most 254 characters") String email,
            @NotBlank(message = "Enter a password")
            @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters") String password,
            @NotNull Role role,
            @Size(max = 100, message = "Job title must be at most 100 characters") String jobTitle,
            @Size(max = 100, message = "Department must be at most 100 characters") String department,
            /** Human-friendly id, unique within the company (blank = none). */
            @Size(max = 32, message = "Staff ID must be at most 32 characters") String staffId,
            @Size(max = 32, message = "Phone number must be at most 32 characters") String phone,
            /** First day of employment — drives first-year leave proration. */
            java.time.LocalDate joinDate,
            UUID reportingManagerId,
            UUID workLocationId,
            @PositiveOrZero @DecimalMax(value = MAX_SALARY, message = "Monthly salary looks too high")
            BigDecimal monthlySalary,
            MaritalStatus maritalStatus,
            Boolean spouseWorking,
            @PositiveOrZero @Max(value = 99, message = "Number of children looks too high") Integer numChildren,
            PayBasis payBasis,
            @Min(value = 1, message = "Pick at least one working day")
            @Max(127) Integer workingDays,
            @PositiveOrZero @DecimalMax(value = "24", message = "Hours per day can't be more than 24") BigDecimal hoursPerDay,
            // Statutory & bank identity (all optional)
            @Size(max = 20, message = "NRIC must be at most 20 characters") String nric,
            @Size(max = 32, message = "EPF number must be at most 32 characters") String epfNo,
            @Size(max = 32, message = "SOCSO number must be at most 32 characters") String socsoNo,
            @Size(max = 32, message = "Tax number must be at most 32 characters") String taxNo,
            @Size(max = 64, message = "Bank name must be at most 64 characters") String bankName,
            @Size(max = 40, message = "Bank account number must be at most 40 characters") String bankAccountNo
    ) {}

    public record ChangeRoleRequest(@NotNull Role role) {}

    /** Partial update of an employee's profile (and optionally role). */
    public record UpdateEmployeeRequest(
            @Size(max = 100, message = "Name must be at most 100 characters") String fullName,
            @Size(max = 100, message = "Job title must be at most 100 characters") String jobTitle,
            @Size(max = 100, message = "Department must be at most 100 characters") String department,
            /** Blank clears it. */
            @Size(max = 32, message = "Phone number must be at most 32 characters") String phone,
            /** Unique within the company; blank clears it. */
            @Size(max = 32, message = "Staff ID must be at most 32 characters") String staffId,
            /** First day of employment — drives first-year leave proration. */
            java.time.LocalDate joinDate,
            Role role,
            /**
             * New reporting manager. Omitted/null leaves the current one UNCHANGED —
             * use {@link #clearReportingManager()} to unassign.
             */
            UUID reportingManagerId,
            /** Set true (with no {@code reportingManagerId}) to unassign the reporting manager. */
            Boolean clearReportingManager,
            /**
             * New assigned work location. Omitted/null leaves the current one UNCHANGED —
             * use {@link #clearWorkLocation()} to unassign.
             */
            UUID workLocationId,
            /** Set true (with no {@code workLocationId}) to unassign the work location. */
            Boolean clearWorkLocation,
            @PositiveOrZero @DecimalMax(value = MAX_SALARY, message = "Monthly salary looks too high")
            BigDecimal monthlySalary,
            MaritalStatus maritalStatus,
            Boolean spouseWorking,
            @PositiveOrZero @Max(value = 99, message = "Number of children looks too high") Integer numChildren,
            PayBasis payBasis,
            @Min(value = 1, message = "Pick at least one working day")
            @Max(127) Integer workingDays,
            @PositiveOrZero @DecimalMax(value = "24", message = "Hours per day can't be more than 24") BigDecimal hoursPerDay,
            // Statutory & bank identity (all optional)
            @Size(max = 20, message = "NRIC must be at most 20 characters") String nric,
            @Size(max = 32, message = "EPF number must be at most 32 characters") String epfNo,
            @Size(max = 32, message = "SOCSO number must be at most 32 characters") String socsoNo,
            @Size(max = 32, message = "Tax number must be at most 32 characters") String taxNo,
            @Size(max = 64, message = "Bank name must be at most 64 characters") String bankName,
            @Size(max = 40, message = "Bank account number must be at most 40 characters") String bankAccountNo,
            // Clear a pay setting back to the company default (column → null). Each is
            // mutually exclusive with its value: sending both is a 400.
            /** True → no salary on file (the employee is not on payroll). */
            Boolean clearMonthlySalary,
            /** True → follow the company's default working days. */
            Boolean clearWorkingDays,
            /** True → follow the company's default hours per day. */
            Boolean clearHoursPerDay,
            /** True → follow the company's default pay basis. */
            Boolean clearPayBasis
    ) {}

    /** Lightweight option for the "reporting manager" picker. */
    public record ManagerOption(UUID id, String fullName, Role role, String jobTitle) {
        public static ManagerOption from(com.teamora.employee.Employee e) {
            return new ManagerOption(e.getId(), e.getFullName(), e.getRole(), e.getJobTitle());
        }
    }

    /** Self-service profile update — phone is the ONLY self-editable field. Blank clears it. */
    public record SelfUpdateRequest(@Size(max = 32, message = "Phone number must be at most 32 characters") String phone) {}

    /** Admin sets a new (temporary) password for an employee. */
    public record ResetPasswordRequest(
            @NotBlank(message = "Enter a new password")
            @Size(min = 8, max = 72, message = "Password must be 8 to 72 characters") String newPassword
    ) {}

    /** Self-service "delete my account" request — the reason is optional. */
    public record DeletionRequest(
            @Size(max = 300, message = "Reason must be at most 300 characters") String reason
    ) {}

    /** When the (current, still-open) deletion request was made — ISO-8601 instant. */
    public record DeletionRequestResponse(java.time.Instant requestedAt) {}
}
