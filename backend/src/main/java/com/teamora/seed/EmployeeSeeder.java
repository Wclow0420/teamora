package com.teamora.seed;

import com.teamora.company.Company;
import com.teamora.company.CompanyRepository;
import com.teamora.company.CompanySettingsService;
import com.teamora.config.TeamoraProperties;
import com.teamora.employee.Employee;
import com.teamora.employee.EmployeeRepository;
import com.teamora.employee.MaritalStatus;
import com.teamora.employee.Role;
import com.teamora.leave.LeaveTypeService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/**
 * Seeds two demo companies (tenants) with people, so multi-tenancy is visible
 * end-to-end. Idempotent: only runs when {@code teamora.seed=true} and the DB
 * has no employees. Runs first (Order 1) so the domain seeders can resolve
 * people (and their company) by email. Demo password for every account:
 * "password".
 */
@Slf4j
@Component
@Order(1)
@RequiredArgsConstructor
public class EmployeeSeeder implements CommandLineRunner {

    public static final String DEMO_PASSWORD = "password";

    private final CompanyRepository companies;
    private final EmployeeRepository employees;
    private final PasswordEncoder passwordEncoder;
    private final TeamoraProperties props;
    private final LeaveTypeService leaveTypeService;
    private final CompanySettingsService companySettingsService;

    @Override
    @Transactional
    public void run(String... args) {
        if (!props.seed() || employees.count() > 0) {
            return;
        }
        String pw = passwordEncoder.encode(DEMO_PASSWORD);

        // ---- Tenant A: Lumi Foods (the rich demo company) ----
        Company lumi = companies.save(company("Lumi Foods Sdn Bhd", "lumi-foods", "Bangsar South HQ"));
        companySettingsService.createDefaults(lumi);
        leaveTypeService.seedDefaults(lumi);
        Employee sarah = emp(lumi, "sarah@lumi.com", "Sarah Lim", Role.HR_ADMIN, "HR Manager", "People", "EMP-001", pw, LocalDate.of(2021, 4, 1));
        Employee nadia = emp(lumi, "nadia@lumi.com", "Nadia Rahman", Role.MANAGER, "Marketing Lead", "Marketing", "EMP-018", pw, LocalDate.of(2023, 5, 15));
        Employee amir = emp(lumi, "amir@lumi.com", "Amir Hakim", Role.EMPLOYEE, "Sales Executive", "Retail", "EMP-042", pw, LocalDate.of(2024, 2, 1));
        Employee weijie = emp(lumi, "weijie@lumi.com", "Wei Jie Tan", Role.EMPLOYEE, "Software Engineer", "Tech", "EMP-031", pw, LocalDate.of(2023, 9, 4));
        Employee priya = emp(lumi, "priya@lumi.com", "Priya Subra", Role.EMPLOYEE, "Accountant", "Finance", "EMP-009", pw, LocalDate.of(2022, 1, 10));
        Employee faizal = emp(lumi, "faizal@lumi.com", "Faizal Omar", Role.EMPLOYEE, "Warehouse", "Ops", "EMP-055", pw, LocalDate.of(2024, 6, 1));
        Employee meiling = emp(lumi, "meiling@lumi.com", "Mei Ling Chong", Role.EMPLOYEE, "HR Executive", "People", "EMP-012", pw, LocalDate.of(2022, 8, 22));
        Employee arjun = emp(lumi, "arjun@lumi.com", "Arjun Nair", Role.EMPLOYEE, "Sales Executive", "Retail", "EMP-040", pw, LocalDate.of(2024, 1, 8));
        Employee siti = emp(lumi, "siti@lumi.com", "Siti Aminah", Role.EMPLOYEE, "Designer", "Marketing", "EMP-027", pw, LocalDate.of(2023, 3, 19));
        Employee imran = emp(lumi, "owner@lumi.com", "Imran Yusof", Role.OWNER, "Founder", "People", "EMP-000", pw, LocalDate.of(2020, 1, 6));

        // Monthly basic salaries — the basis for payroll runs (matches the seeded June payslips).
        imran.setMonthlySalary(money(12000));
        sarah.setMonthlySalary(money(9500));
        nadia.setMonthlySalary(money(6200));
        amir.setMonthlySalary(money(4000));
        weijie.setMonthlySalary(money(7000));
        priya.setMonthlySalary(money(5800));
        faizal.setMonthlySalary(money(2800));
        meiling.setMonthlySalary(money(5200));
        arjun.setMonthlySalary(money(4100));
        siti.setMonthlySalary(money(4600));

        // Demo tax profiles (for PCB) — a mix so payroll shows realistic variation.
        // Everyone else defaults to single, no children (null marital status → single).
        imran.setMaritalStatus(MaritalStatus.MARRIED);
        imran.setSpouseWorking(false);
        imran.setNumChildren(3);
        sarah.setMaritalStatus(MaritalStatus.MARRIED);
        sarah.setSpouseWorking(true);
        sarah.setNumChildren(1);
        amir.setMaritalStatus(MaritalStatus.MARRIED);
        amir.setSpouseWorking(false);
        amir.setNumChildren(2);

        employees.saveAll(java.util.List.of(
                imran, sarah, nadia, amir, weijie, priya, faizal, meiling, arjun, siti));

        // Reporting structure: Retail/Marketing report to Nadia (MANAGER); the
        // rest to Sarah (HR_ADMIN). Nadia & Sarah report to the owner (null).
        amir.setReportingManager(nadia);
        arjun.setReportingManager(nadia);
        siti.setReportingManager(nadia);
        weijie.setReportingManager(sarah);
        priya.setReportingManager(sarah);
        faizal.setReportingManager(sarah);
        meiling.setReportingManager(sarah);
        employees.saveAll(java.util.List.of(amir, arjun, siti, weijie, priya, faizal, meiling));

        // ---- Tenant B: Nusantara Tech (proves isolation) ----
        Company nusantara = companies.save(company("Nusantara Tech Sdn Bhd", "nusantara-tech", "Cyberjaya HQ"));
        companySettingsService.createDefaults(nusantara);
        leaveTypeService.seedDefaults(nusantara);
        Employee daniel = emp(nusantara, "admin@nusantara.com", "Daniel Wong", Role.OWNER, "CEO", "Leadership", "NT-001", pw, LocalDate.of(2019, 7, 1));
        Employee budi = emp(nusantara, "budi@nusantara.com", "Budi Santoso", Role.EMPLOYEE, "Backend Engineer", "Engineering", "NT-014", pw, LocalDate.of(2023, 2, 13));
        daniel.setMonthlySalary(money(15000));
        budi.setMonthlySalary(money(6500));
        employees.saveAll(java.util.List.of(daniel, budi));

        log.info("Seeded {} companies and {} employees (password: '{}')", companies.count(), employees.count(), DEMO_PASSWORD);
    }

    private Company company(String name, String slug, String hq) {
        return Company.builder()
                .name(name)
                .slug(slug)
                .timezone("Asia/Kuala_Lumpur")
                .currency("MYR")
                .address(hq)
                .active(true)
                .build();
    }

    private Employee emp(Company company, String email, String name, Role role, String title, String dept, String staffId, String pw, LocalDate joined) {
        Employee e = Employee.builder()
                .email(email)
                .passwordHash(pw)
                .fullName(name)
                .role(role)
                .jobTitle(title)
                .department(dept)
                .location(company.getAddress())
                .staffId(staffId)
                .joinDate(joined)
                .active(true)
                .build();
        e.setCompany(company);
        return e;
    }

    private static BigDecimal money(double v) {
        return BigDecimal.valueOf(v).setScale(2, java.math.RoundingMode.HALF_UP);
    }
}
