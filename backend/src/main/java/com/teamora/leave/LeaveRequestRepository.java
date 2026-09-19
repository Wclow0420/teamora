package com.teamora.leave;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {

    List<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(UUID employeeId);

    // All pending in the company (HR_ADMIN / OWNER override view).
    @Query("""
            select r from LeaveRequest r
            join fetch r.employee e
            where r.status = :status
              and r.company.id = :companyId
            order by r.createdAt desc
            """)
    List<LeaveRequest> findByStatusAndCompanyId(@Param("status") LeaveStatus status,
                                                @Param("companyId") UUID companyId);

    // Pending requests routed to a specific reporting manager.
    @Query("""
            select r from LeaveRequest r
            join fetch r.employee e
            where r.status = :status
              and r.company.id = :companyId
              and e.reportingManager.id = :managerId
            order by r.createdAt desc
            """)
    List<LeaveRequest> findPendingForReportingManager(@Param("status") LeaveStatus status,
                                                      @Param("companyId") UUID companyId,
                                                      @Param("managerId") UUID managerId);

    // Single request with requester + their reporting manager, for the decide check.
    @Query("select r from LeaveRequest r join fetch r.employee e left join fetch e.reportingManager left join fetch r.company where r.id = :id")
    Optional<LeaveRequest> findByIdWithEmployeeAndManager(@Param("id") UUID id);

    // Most-recently-touched requests in the company, employee fetch-joined (dashboard activity feed).
    @Query("""
            select r from LeaveRequest r
            join fetch r.employee e
            where r.company.id = :companyId
            order by r.updatedAt desc
            """)
    List<LeaveRequest> findRecentByCompany(@Param("companyId") UUID companyId,
                                           org.springframework.data.domain.Pageable pageable);
}
