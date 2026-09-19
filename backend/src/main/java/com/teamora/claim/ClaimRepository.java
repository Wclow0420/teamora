package com.teamora.claim;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ClaimRepository extends JpaRepository<Claim, UUID> {

    List<Claim> findByEmployeeIdOrderByClaimDateDesc(UUID employeeId);

    // All pending in the company (HR_ADMIN / OWNER override view).
    @Query("""
            select c from Claim c
            join fetch c.employee
            where c.status = :status
              and c.company.id = :companyId
            order by c.claimDate desc
            """)
    List<Claim> findByStatusAndCompanyId(@Param("status") ClaimStatus status,
                                         @Param("companyId") UUID companyId);

    // Pending claims routed to a specific reporting manager.
    @Query("""
            select c from Claim c
            join fetch c.employee e
            where c.status = :status
              and c.company.id = :companyId
              and e.reportingManager.id = :managerId
            order by c.claimDate desc
            """)
    List<Claim> findPendingForReportingManager(@Param("status") ClaimStatus status,
                                               @Param("companyId") UUID companyId,
                                               @Param("managerId") UUID managerId);

    // Single claim with requester + their reporting manager, for the decide check.
    @Query("select c from Claim c join fetch c.employee e left join fetch e.reportingManager left join fetch c.company where c.id = :id")
    Optional<Claim> findByIdWithEmployeeAndManager(@Param("id") UUID id);

    @Query("""
            select coalesce(sum(c.amount), 0) from Claim c
            where c.employee.id = :employeeId
              and c.status = :status
              and c.claimDate >= :from and c.claimDate < :to
            """)
    BigDecimal sumAmount(@Param("employeeId") UUID employeeId,
                         @Param("status") ClaimStatus status,
                         @Param("from") LocalDate from,
                         @Param("to") LocalDate to);

    // Most-recently-touched claims in the company, employee fetch-joined (dashboard activity feed).
    @Query("""
            select c from Claim c
            join fetch c.employee e
            where c.company.id = :companyId
            order by c.updatedAt desc
            """)
    List<Claim> findRecentByCompany(@Param("companyId") UUID companyId,
                                    org.springframework.data.domain.Pageable pageable);
}
