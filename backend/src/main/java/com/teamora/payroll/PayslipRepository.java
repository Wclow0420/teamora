package com.teamora.payroll;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PayslipRepository extends JpaRepository<Payslip, UUID> {

    /** A staff member's payslip history, newest period first. */
    List<Payslip> findByEmployeeIdOrderByPeriodDesc(UUID employeeId);

    /** A single payslip for one employee + period. */
    Optional<Payslip> findByEmployeeIdAndPeriod(UUID employeeId, String period);

    /** All payslips in a period for one company with the employee eagerly fetched (admin run summary). */
    @Query("select p from Payslip p join fetch p.employee where p.period = :period and p.company.id = :companyId")
    List<Payslip> findByPeriodAndCompanyId(@Param("period") String period, @Param("companyId") UUID companyId);

    /** The most recent period present for one company, or null if none (default summary period). */
    @Query("select max(p.period) from Payslip p where p.company.id = :companyId")
    String findLatestPeriodByCompanyId(@Param("companyId") UUID companyId);
}
