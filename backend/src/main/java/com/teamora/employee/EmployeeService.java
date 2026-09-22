package com.teamora.employee;

import com.teamora.common.exception.BadRequestException;
import com.teamora.common.exception.ResourceNotFoundException;
import com.teamora.company.Company;
import com.teamora.employee.dto.EmployeeDtos.ChangeRoleRequest;
import com.teamora.employee.dto.EmployeeDtos.CreateEmployeeRequest;
import com.teamora.employee.dto.EmployeeDtos.ManagerOption;
import com.teamora.employee.dto.EmployeeDtos.UpdateEmployeeRequest;
import com.teamora.employee.dto.EmployeeResponse;
import com.teamora.location.WorkLocation;
import com.teamora.location.WorkLocationRepository;
import com.teamora.payroll.CompensationService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.YearMonth;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class EmployeeService {

    private final EmployeeRepository employees;
    private final PasswordEncoder passwordEncoder;
    private final CompensationService compensationService;
    private final WorkLocationRepository workLocations;

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
                .staffId(req.staffId())
                .phone(req.phone())
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
                .location(company.getName())
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
        Employee e = employees.findByIdAndCompanyId(id, companyId)
                .orElseThrow(() -> ResourceNotFoundException.of("Employee", id));

        if (req.fullName() != null && !req.fullName().isBlank()) e.setFullName(req.fullName().trim());
        if (req.jobTitle() != null) e.setJobTitle(req.jobTitle().isBlank() ? null : req.jobTitle().trim());
        if (req.department() != null) e.setDepartment(req.department().isBlank() ? null : req.department().trim());
        if (req.phone() != null) e.setPhone(req.phone().isBlank() ? null : req.phone().trim());
        if (req.staffId() != null) e.setStaffId(req.staffId().isBlank() ? null : req.staffId().trim());
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
        e.setReportingManager(resolveReportingManager(companyId, req.reportingManagerId(), e.getId()));
        e.setWorkLocation(resolveWorkLocation(companyId, req.workLocationId()));

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

    // ---- helpers ----

    /** Trim a nullable string, mapping blank → null (so optional identity fields don't store empty strings). */
    private static String trimToNull(String s) {
        if (s == null) return null;
        String t = s.trim();
        return t.isEmpty() ? null : t;
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

    /** Resolve an assigned work location within the company; null id clears the assignment. */
    private WorkLocation resolveWorkLocation(UUID companyId, UUID workLocationId) {
        if (workLocationId == null) return null;
        return workLocations.findByIdAndCompanyId(workLocationId, companyId)
                .orElseThrow(() -> new BadRequestException("Work location not found in your company"));
    }
}
