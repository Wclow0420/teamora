package com.teamora.employee;

import com.teamora.auth.RefreshTokenRepository;
import com.teamora.common.exception.BadRequestException;
import com.teamora.common.exception.ResourceNotFoundException;
import com.teamora.company.Company;
import com.teamora.employee.dto.EmployeeDtos.ChangeRoleRequest;
import com.teamora.employee.dto.EmployeeDtos.CreateEmployeeRequest;
import com.teamora.employee.dto.EmployeeDtos.ManagerOption;
import com.teamora.employee.dto.EmployeeDtos.SelfUpdateRequest;
import com.teamora.employee.dto.EmployeeDtos.UpdateEmployeeRequest;
import com.teamora.employee.dto.EmployeeResponse;
import com.teamora.leave.LeaveBalanceService;
import com.teamora.location.WorkLocation;
import com.teamora.notification.PushTokenRepository;
import com.teamora.location.WorkLocationRepository;
import com.teamora.payroll.CompensationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeService {

    private final EmployeeRepository employees;
    private static final int MAX_PHONE_LENGTH = 32;

    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenRepository refreshTokens;
    private final CompensationService compensationService;
    private final WorkLocationRepository workLocations;
    private final LeaveBalanceService leaveBalances;
    private final PushTokenRepository pushTokens;

    /** Directory listing — scoped to the caller's company. */
    public List<EmployeeResponse> search(UUID companyId, String department, String query) {
        String dept = (department == null || department.isBlank() || department.equalsIgnoreCase("all")) ? "" : department;
        String q = (query == null || query.isBlank()) ? "" : query;
        return employees.searchWithManager(companyId, dept, q).stream().map(EmployeeResponse::withManager).toList();
    }

    public EmployeeResponse get(UUID companyId, UUID id) {
        return employees.findByIdAndCompanyIdWithManager(id, companyId)
                .map(this::toDetail)
                .orElseThrow(() -> ResourceNotFoundException.of("Employee", id));
    }

    /** Adds the effective schedule + indicative derived daily/hourly rates for the current month. */
    private EmployeeResponse toDetail(Employee e) {
        CompensationService.Derivation d = compensationService.derive(e, YearMonth.now());
        return EmployeeResponse.withComp(e,
                d.schedule().payBasis(),
                d.schedule().workingDaysMask(),
                d.schedule().hoursPerDay(),
                d.dailyRate(),
                d.hourlyRate());
    }

    public long activeHeadcount(UUID companyId) {
        return employees.countByCompanyIdAndActiveTrue(companyId);
    }

    /** Managers assignable as a reporting manager (OWNER / HR_ADMIN / MANAGER). */
    public List<ManagerOption> assignableManagers(UUID companyId) {
        return employees.findByCompanyIdAndRoleInAndActiveTrue(companyId, List.of(Role.OWNER, Role.HR_ADMIN, Role.MANAGER))
                .stream().map(ManagerOption::from).toList();
    }

    /** Add an employee to the given company (admin action). */
    @Transactional
    public EmployeeResponse create(Company company, CreateEmployeeRequest req) {
        rejectOwnerRole(req.role());
        if (employees.existsByEmailIgnoreCase(req.email())) {
            throw new BadRequestException("An account with this email already exists");
        }
        Employee e = Employee.builder()
                .email(req.email().trim())
                .passwordHash(passwordEncoder.encode(req.password()))
                .fullName(req.fullName())
                .role(req.role())
                .jobTitle(req.jobTitle())
                .department(req.department())
                .staffId(uniqueStaffId(company.getId(), req.staffId(), null))
                .phone(trimToNull(req.phone()))
                .joinDate(req.joinDate())
                .monthlySalary(req.monthlySalary())
                .maritalStatus(req.maritalStatus())
                .spouseWorking(req.spouseWorking())
                .numChildren(req.numChildren() != null ? req.numChildren() : 0)
                .payBasis(req.payBasis())
                .workingDays(req.workingDays() != null ? req.workingDays().shortValue() : null)
                .hoursPerDay(req.hoursPerDay())
                .nric(trimToNull(req.nric()))
                .epfNo(trimToNull(req.epfNo()))
                .socsoNo(trimToNull(req.socsoNo()))
                .taxNo(trimToNull(req.taxNo()))
                .bankName(trimToNull(req.bankName()))
                .bankAccountNo(trimToNull(req.bankAccountNo()))
                .active(true)
                .build();
        e.setCompany(company);
        e.setReportingManager(resolveReportingManager(company.getId(), req.reportingManagerId(), null));
        e.setWorkLocation(resolveWorkLocation(company.getId(), req.workLocationId()));
        return toDetail(employees.save(e));
    }

    @Transactional
    public EmployeeResponse changeRole(UUID companyId, UUID id, ChangeRoleRequest req) {
        rejectOwnerRole(req.role());
        Employee e = employees.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> ResourceNotFoundException.of("Employee", id));
        if (e.getRole() == Role.OWNER) {
            throw new BadRequestException("The owner's role can only change via transfer-ownership");
        }
        e.setRole(req.role());
        return EmployeeResponse.from(e);
    }

    /** Update an employee's profile (and optionally role) within the caller's company. */
    @Transactional
    public EmployeeResponse update(Employee caller, UUID id, UpdateEmployeeRequest req) {
        UUID companyId = caller.getCompany().getId();
        // Fetch-join the manager + work location: now that a PATCH can leave them
        // untouched, the response must still report the values already on the row
        // (an uninitialized lazy proxy reads back as "unassigned").
        Employee e = employees.findByIdAndCompanyIdWithManager(id, companyId)
                .orElseThrow(() -> ResourceNotFoundException.of("Employee", id));

        if (req.fullName() != null && !req.fullName().isBlank()) e.setFullName(req.fullName().trim());
        if (req.jobTitle() != null) e.setJobTitle(req.jobTitle().isBlank() ? null : req.jobTitle().trim());
        if (req.department() != null) e.setDepartment(req.department().isBlank() ? null : req.department().trim());
        if (req.phone() != null) e.setPhone(req.phone().isBlank() ? null : req.phone().trim());
        if (req.staffId() != null) e.setStaffId(uniqueStaffId(companyId, req.staffId(), e.getId()));
        if (req.joinDate() != null && !req.joinDate().equals(e.getJoinDate())) {
            LocalDate previousJoinDate = e.getJoinDate();
            e.setJoinDate(req.joinDate());
            // First-year leave is prorated from the join date — keep this leave year's
            // (non-overridden) entitlements in step with the corrected date.
            leaveBalances.reprorateForJoinDateChange(e, previousJoinDate);
        }
        if (req.monthlySalary() != null) e.setMonthlySalary(req.monthlySalary());
        if (req.maritalStatus() != null) e.setMaritalStatus(req.maritalStatus());
        if (req.spouseWorking() != null) e.setSpouseWorking(req.spouseWorking());
        if (req.numChildren() != null) e.setNumChildren(req.numChildren());
        if (req.payBasis() != null) e.setPayBasis(req.payBasis());
        if (req.workingDays() != null) e.setWorkingDays(req.workingDays().shortValue());
        if (req.hoursPerDay() != null) e.setHoursPerDay(req.hoursPerDay());
        if (req.nric() != null) e.setNric(trimToNull(req.nric()));
        if (req.epfNo() != null) e.setEpfNo(trimToNull(req.epfNo()));
        if (req.socsoNo() != null) e.setSocsoNo(trimToNull(req.socsoNo()));
        if (req.taxNo() != null) e.setTaxNo(trimToNull(req.taxNo()));
        if (req.bankName() != null) e.setBankName(trimToNull(req.bankName()));
        if (req.bankAccountNo() != null) e.setBankAccountNo(trimToNull(req.bankAccountNo()));

        // Associations are tri-state on a PATCH: an id reassigns, an explicit clear flag
        // unassigns, and omitting both leaves the current value alone. (Before the clear
        // flags existed these two lines ran unconditionally, so a PATCH of any single
        // field silently wiped the manager + work location.)
        if (req.reportingManagerId() != null) {
            e.setReportingManager(resolveReportingManager(companyId, req.reportingManagerId(), e.getId()));
        } else if (Boolean.TRUE.equals(req.clearReportingManager())) {
            e.setReportingManager(null);
        }
        if (req.workLocationId() != null) {
            e.setWorkLocation(resolveWorkLocation(companyId, req.workLocationId()));
        } else if (Boolean.TRUE.equals(req.clearWorkLocation())) {
            e.setWorkLocation(null);
        }

        if (req.role() != null && req.role() != e.getRole()) {
            rejectOwnerRole(req.role());
            if (e.getRole() == Role.OWNER) {
                throw new BadRequestException("The owner's role can only change via transfer-ownership");
            }
            if (e.getId().equals(caller.getId())) {
                throw new BadRequestException("You cannot change your own role");
            }
            e.setRole(req.role());
        }
        return toDetail(e);
    }

    /**
     * Hand over ownership: the current owner becomes HR_ADMIN and the target
     * becomes OWNER. Demote-then-flush-then-promote so the single-owner partial
     * unique index is never transiently violated.
     */
    @Transactional
    public EmployeeResponse transferOwnership(Employee caller, UUID targetId) {
        UUID companyId = caller.getCompany().getId();
        Employee current = employees.findByIdAndCompanyId(caller.getId(), companyId)
                .orElseThrow(() -> ResourceNotFoundException.of("Employee", caller.getId()));
        if (current.getRole() != Role.OWNER) {
            throw new AccessDeniedException("Only the owner can transfer ownership");
        }
        Employee target = employees.findByIdAndCompanyId(targetId, companyId)
                .orElseThrow(() -> ResourceNotFoundException.of("Employee", targetId));
        if (target.getId().equals(current.getId())) {
            throw new BadRequestException("You are already the owner");
        }
        if (!target.isActive()) {
            throw new BadRequestException("Cannot transfer ownership to an inactive employee");
        }

        current.setRole(Role.HR_ADMIN);
        employees.saveAndFlush(current);   // demote hits the DB first
        target.setRole(Role.OWNER);
        employees.saveAndFlush(target);    // now only one OWNER row for this company
        return EmployeeResponse.from(target);
    }

    /** Self-service: the caller updates their own phone (the only self-editable field). */
    @Transactional
    public EmployeeResponse updateOwnPhone(Employee caller, SelfUpdateRequest req) {
        Employee e = employees.findByIdAndCompanyIdWithManager(caller.getId(), caller.getCompany().getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Employee", caller.getId()));
        if (req != null && req.phone() != null) {
            String phone = trimToNull(req.phone());
            if (phone != null && phone.length() > MAX_PHONE_LENGTH) {
                throw new BadRequestException("Phone number must be at most " + MAX_PHONE_LENGTH + " characters");
            }
            e.setPhone(phone);
        }
        return toDetail(e);
    }

    /**
     * Admin (OWNER/HR_ADMIN) sets a new password for an employee in their company
     * and signs that employee out everywhere (all refresh tokens revoked). Only
     * the OWNER may reset the OWNER's password.
     */
    @Transactional
    public void resetPassword(Employee caller, UUID id, String newPassword) {
        Employee e = employees.findByIdAndCompanyId(id, caller.getCompany().getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Employee", id));
        if (e.getRole() == Role.OWNER && caller.getRole() != Role.OWNER) {
            throw new AccessDeniedException("Only the owner can reset the owner's password");
        }
        if (e.getId().equals(caller.getId())) {
            // Changing your own password must prove you know the current one.
            throw new BadRequestException("Use Change password to change your own password");
        }
        e.setPasswordHash(passwordEncoder.encode(newPassword));
        refreshTokens.revokeAllForEmployee(e.getId(), "");
        // Their devices are being signed out — stop pushing this account's alerts to them.
        pushTokens.deleteByEmployeeId(e.getId());
    }

    // ---- helpers ----

    /** Trim a nullable string, mapping blank → null (so optional identity fields don't store empty strings). */
    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
    }

    /**
     * Normalise a staff id (blank → null) and make sure no one else in the company
     * already uses it. {@code selfIdOrNull} is the employee being edited, if any.
     */
    private String uniqueStaffId(UUID companyId, String raw, UUID selfIdOrNull) {
        String staffId = trimToNull(raw);
        if (staffId == null) {
            return null;
        }
        boolean taken = selfIdOrNull == null
                ? employees.existsByCompanyIdAndStaffIdIgnoreCase(companyId, staffId)
                : employees.existsByCompanyIdAndStaffIdIgnoreCaseAndIdNot(companyId, staffId, selfIdOrNull);
        if (taken) {
            throw new BadRequestException("Staff ID " + staffId + " is already used by someone else in your company");
        }
        return staffId;
    }

    private void rejectOwnerRole(Role role) {
        if (role == Role.OWNER) {
            throw new BadRequestException("OWNER can't be assigned here — use transfer ownership");
        }
    }

    private Employee resolveReportingManager(UUID companyId, UUID managerId, UUID selfIdOrNull) {
        if (managerId == null) return null;
        if (managerId.equals(selfIdOrNull)) {
            throw new BadRequestException("An employee cannot report to themselves");
        }
        Employee mgr = employees.findByIdAndCompanyId(managerId, companyId)
                .orElseThrow(() -> new BadRequestException("Reporting manager not found in your company"));
        if (mgr.getRole() == Role.EMPLOYEE) {
            throw new BadRequestException("Reporting manager must be a manager, HR admin or owner");
        }
        return mgr;
    }

    /** Resolve an assigned work location within the company; a null id means "no location". */
    private WorkLocation resolveWorkLocation(UUID companyId, UUID workLocationId) {
        if (workLocationId == null) return null;
        return workLocations.findByIdAndCompanyId(workLocationId, companyId)
                .orElseThrow(() -> new BadRequestException("Work location not found in your company"));
    }
}
