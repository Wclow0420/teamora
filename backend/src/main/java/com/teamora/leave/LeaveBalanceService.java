package com.teamora.leave;

import com.teamora.common.exception.BadRequestException;
import com.teamora.common.exception.ResourceNotFoundException;
import com.teamora.company.Company;
import com.teamora.company.CompanySettingsService;
import com.teamora.employee.Employee;
import com.teamora.employee.EmployeeRepository;
import com.teamora.leave.dto.LeaveBalanceResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * Owns leave entitlement over time: which leave year a balance belongs to, how much
 * of it has accrued, what rolled over from last year, and how a mid-year joiner's
 * first year is prorated.
 *
 * <h2>Lazy provisioning (no scheduler)</h2>
 * Rows are created on demand by {@link #ensureBalances(Employee, int)} whenever
 * balances are read or leave is charged, for every ACTIVE leave type in the company.
 * It is idempotent, so a cron job to "open" each new leave year is not needed.
 *
 * <h2>Entitlement</h2>
 * A new row starts at the leave type's {@code defaultEntitlementDays}, then:
 * <ul>
 *   <li><b>Join-date proration (first leave year only).</b> If the employee's
 *       {@code joinDate} falls inside this leave year,
 *       {@code entitled = round2(full x remainingMonths / 12)} where
 *       {@code remainingMonths = 12 - monthIndexWithinYear(joinDate)} — the join month
 *       counts as a full month, so someone joining in the 9th month of the year gets
 *       4/12. An employee with no {@code joinDate} is never prorated, and one whose
 *       {@code joinDate} is after this leave year ends gets nothing.</li>
 *   <li><b>Carry-forward.</b> If a row exists for the previous leave year,
 *       {@code carriedForward = min(max(prev.entitled + prev.carriedForward - prev.used, 0),
 *       leaveType.carryForwardMaxDays)}. There is no expiry window.</li>
 * </ul>
 *
 * <h2>Accrual (derived at read time, never stored)</h2>
 * See {@link LeaveBalance#accruedToDate(int, java.time.LocalDate)} — FIXED_ANNUAL grants
 * everything immediately, MONTHLY_ACCRUAL grants {@code entitled x elapsedMonths / 12},
 * and NONE tracks no entitlement at all (e.g. unpaid leave).
 */
@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class LeaveBalanceService {

    private static final int DAY_SCALE = LeaveBalance.DAY_SCALE;

    private final LeaveBalanceRepository balances;
    private final LeaveTypeRepository leaveTypes;
    private final EmployeeRepository employees;
    private final CompanySettingsService companySettings;

    // ---------- leave-year resolution ----------

    /** The calendar month (1–12) this company's leave year starts on. */
    public int startMonth(Company company) {
        return LeaveYear.normaliseStartMonth(companySettings.resolve(company).getLeaveYearStartMonth());
    }

    /** The leave year we are in today, for this company. */
    public int currentLeaveYear(Company company) {
        return LeaveYear.yearOf(LocalDate.now(), startMonth(company));
    }

    /** The leave year containing {@code date}, for this company. */
    public int leaveYearOf(Company company, LocalDate date) {
        return LeaveYear.yearOf(date, startMonth(company));
    }

    // ---------- reads ----------

    /** An employee's balances for {@code year} (default: the current leave year), provisioning any missing rows. */
    @Transactional
    public List<LeaveBalanceResponse> balancesFor(Employee employee, Integer year) {
        int startMonth = startMonth(employee.getCompany());
        int leaveYear = year != null ? year : LeaveYear.yearOf(LocalDate.now(), startMonth);
        LocalDate today = LocalDate.now();
        return ensureBalances(employee, leaveYear).stream()
                .map(b -> LeaveBalanceResponse.from(b, startMonth, today))
                .toList();
    }

    /** Admin view of another employee's balances, scoped to the caller's company. */
    @Transactional
    public List<LeaveBalanceResponse> balancesForEmployee(Employee caller, UUID employeeId, Integer year) {
        return balancesFor(requireInCompany(caller, employeeId), year);
    }

    /**
     * Days available to {@code employee} for a leave type in the year containing
     * {@code onDate}, or {@code null} when no balance row exists yet.
     */
    public BigDecimal availableOn(Employee employee, UUID leaveTypeId, LocalDate onDate) {
        int startMonth = startMonth(employee.getCompany());
        int leaveYear = LeaveYear.yearOf(onDate, startMonth);
        return balances.findByEmployeeIdAndLeaveTypeIdAndLeaveYear(employee.getId(), leaveTypeId, leaveYear)
                .map(b -> b.available(startMonth, LocalDate.now()))
                .orElse(null);
    }

    // ---------- lazy provisioning ----------

    /**
     * Balances for every ACTIVE leave type in the employee's company for {@code leaveYear},
     * creating whatever is missing. Idempotent — safe to call on every read.
     */
    @Transactional
    public List<LeaveBalance> ensureBalances(Employee employee, int leaveYear) {
        Company company = employee.getCompany();
        int startMonth = startMonth(company);
        List<LeaveType> types = leaveTypes.findByCompanyIdAndActiveTrueOrderBySortOrderAscNameAsc(company.getId());

        List<LeaveBalance> existing = balances.findByEmployeeIdAndLeaveYear(employee.getId(), leaveYear);
        List<LeaveBalance> result = new ArrayList<>(existing);
        for (LeaveType type : types) {
            boolean present = existing.stream().anyMatch(b -> b.getLeaveType().getId().equals(type.getId()));
            if (!present) {
                result.add(create(employee, type, leaveYear, startMonth));
            }
        }
        result.sort(java.util.Comparator
                .comparingInt((LeaveBalance b) -> b.getLeaveType().getSortOrder())
                .thenComparing(b -> b.getLeaveType().getName()));
        return result;
    }

    /** One balance row for the given type + leave year, creating it if absent. */
    @Transactional
    public LeaveBalance ensureBalance(Employee employee, LeaveType type, int leaveYear) {
        return balances.findByEmployeeIdAndLeaveTypeIdAndLeaveYear(employee.getId(), type.getId(), leaveYear)
                .orElseGet(() -> create(employee, type, leaveYear, startMonth(employee.getCompany())));
    }

    private LeaveBalance create(Employee employee, LeaveType type, int leaveYear, int startMonth) {
        LeaveBalance b = LeaveBalance.builder()
                .employee(employee)
                .leaveType(type)
                .leaveYear(leaveYear)
                .entitled(entitlementFor(employee, type, leaveYear, startMonth))
                .carriedForward(carryForwardInto(employee, type, leaveYear))
                .used(BigDecimal.ZERO.setScale(DAY_SCALE))
                .build();
        b.setCompany(employee.getCompany());
        return balances.save(b);
    }

    // ---------- entitlement math ----------

    /** Full-year entitlement, prorated when the employee joined during this leave year. */
    BigDecimal entitlementFor(Employee employee, LeaveType type, int leaveYear, int startMonth) {
        BigDecimal full = BigDecimal.valueOf(type.getDefaultEntitlementDays()).setScale(DAY_SCALE);
        LocalDate joinDate = employee.getJoinDate();
        if (joinDate == null || full.signum() == 0) {
            return full;
        }
        int joinYear = LeaveYear.yearOf(joinDate, startMonth);
        if (joinYear < leaveYear) {
            return full;                                    // already a full-year employee
        }
        if (joinYear > leaveYear) {
            return BigDecimal.ZERO.setScale(DAY_SCALE);      // not employed in that year yet
        }
        int remainingMonths = 12 - LeaveYear.monthIndexWithinYear(joinDate, startMonth);
        return full.multiply(BigDecimal.valueOf(remainingMonths))
                .divide(BigDecimal.valueOf(12), DAY_SCALE, RoundingMode.HALF_UP);
    }

    /** Unused days rolled in from {@code leaveYear − 1}, capped by the leave type. */
    BigDecimal carryForwardInto(Employee employee, LeaveType type, int leaveYear) {
        BigDecimal cap = type.carryForwardMaxDaysOrZero();
        if (cap.signum() <= 0) {
            return BigDecimal.ZERO.setScale(DAY_SCALE);
        }
        return balances.findByEmployeeIdAndLeaveTypeIdAndLeaveYear(employee.getId(), type.getId(), leaveYear - 1)
                .map(prev -> prev.availableAtYearEnd().max(BigDecimal.ZERO).min(cap).setScale(DAY_SCALE, RoundingMode.HALF_UP))
                .orElse(BigDecimal.ZERO.setScale(DAY_SCALE));
    }

    // ---------- admin override ----------

    /**
     * Set an employee's entitlement for one leave type + leave year (seniority-based
     * leave, negotiated packages, corrections). Owners / HR admins only — the caller's
     * company scopes both the employee and the leave type.
     */
    @Transactional
    public LeaveBalanceResponse override(Employee caller, UUID employeeId, UUID leaveTypeId,
                                         Integer year, BigDecimal entitled) {
        if (entitled == null || entitled.signum() < 0) {
            throw new BadRequestException("Entitled days must be zero or more");
        }
        Employee employee = requireInCompany(caller, employeeId);
        UUID companyId = caller.getCompany().getId();
        LeaveType type = leaveTypes.findByIdAndCompanyId(leaveTypeId, companyId)
                .orElseThrow(() -> ResourceNotFoundException.of("LeaveType", leaveTypeId));

        int startMonth = startMonth(caller.getCompany());
        int leaveYear = year != null ? year : LeaveYear.yearOf(LocalDate.now(), startMonth);

        LeaveBalance b = ensureBalance(employee, type, leaveYear);
        b.setEntitled(entitled.setScale(DAY_SCALE, RoundingMode.HALF_UP));
        return LeaveBalanceResponse.from(b, startMonth, LocalDate.now());
    }

    private Employee requireInCompany(Employee caller, UUID employeeId) {
        return employees.findByIdAndCompanyId(employeeId, caller.getCompany().getId())
                .orElseThrow(() -> ResourceNotFoundException.of("Employee", employeeId));
    }
}
