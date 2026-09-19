package com.teamora.overtime;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface OvertimeRepository extends JpaRepository<OvertimeRequest, UUID> {

    List<OvertimeRequest> findByEmployeeIdOrderByWorkDateDesc(UUID employeeId);

    /** Total overtime hours for one employee in [from, to) at a given status (payroll run). */
    @Query("""
            select coalesce(sum(o.hours), 0) from OvertimeRequest o
            where o.employee.id = :employeeId
              and o.status = :status
              and o.workDate >= :from and o.workDate < :to
            """)
    BigDecimal sumHoursInPeriod(@Param("employeeId") UUID employeeId,
                                @Param("status") OvertimeStatus status,
                                @Param("from") LocalDate from,
                                @Param("to") LocalDate to);

    // All pending in the company (HR_ADMIN / OWNER override view).
    @Query("""
            select o from OvertimeRequest o
            join fetch o.employee e
            where o.status = :status and o.company.id = :companyId
            order by o.workDate desc
            """)
    List<OvertimeRequest> findByStatusAndCompanyId(@Param("status") OvertimeStatus status,
                                                   @Param("companyId") UUID companyId);

    // Pending routed to a specific reporting manager.
    @Query("""
            select o from OvertimeRequest o
            join fetch o.employee e
            where o.status = :status and o.company.id = :companyId
              and e.reportingManager.id = :managerId
            order by o.workDate desc
            """)
    List<OvertimeRequest> findPendingForReportingManager(@Param("status") OvertimeStatus status,
                                                         @Param("companyId") UUID companyId,
                                                         @Param("managerId") UUID managerId);

    // Single request with requester + manager, for the decide check.
    @Query("select o from OvertimeRequest o join fetch o.employee e left join fetch e.reportingManager left join fetch o.company where o.id = :id")
    Optional<OvertimeRequest> findByIdWithEmployeeAndManager(@Param("id") UUID id);

    // Most-recently-touched overtime requests in the company, employee fetch-joined (dashboard activity feed).
    @Query("""
            select o from OvertimeRequest o
            join fetch o.employee e
            where o.company.id = :companyId
            order by o.updatedAt desc
            """)
    List<OvertimeRequest> findRecentByCompany(@Param("companyId") UUID companyId,
                                              org.springframework.data.domain.Pageable pageable);
}
