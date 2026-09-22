package com.teamora.leave;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveRequestRepository extends JpaRepository<LeaveRequest, UUID> {

    @Query("""
            select r from LeaveRequest r
            join fetch r.leaveType t
            where r.employee.id = :employeeId
            order by r.createdAt desc
            """)
    List<LeaveRequest> findByEmployeeIdOrderByCreatedAtDesc(@Param("employeeId") UUID employeeId);

    /** Approved leave for one employee overlapping [from, to] (payroll compensation). */
    @Query("""
            select r from LeaveRequest r
            join fetch r.leaveType t
            where r.employee.id = :employeeId
              and r.status = :status
              and r.startDate <= :to
              and r.endDate >= :from
            """)
    List<LeaveRequest> findApprovedOverlapping(@Param("employeeId") UUID employeeId,
                                               @Param("status") LeaveStatus status,
                                               @Param("from") java.time.LocalDate from,
                                               @Param("to") java.time.LocalDate to);

    // All pending in the company (HR_ADMIN / OWNER override view).
    @Query("""
            select r from LeaveRequest r
            join fetch r.employee e
            join fetch r.leaveType t
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
            join fetch r.leaveType t
            where r.status = :status
              and r.company.id = :companyId
              and e.reportingManager.id = :managerId
            order by r.createdAt desc
            """)
    List<LeaveRequest> findPendingForReportingManager(@Param("status") LeaveStatus status,
                                                      @Param("companyId") UUID companyId,
                                                      @Param("managerId") UUID managerId);

    // Single request with requester + their reporting manager + leave type, for the decide check.
    @Query("select r from LeaveRequest r join fetch r.employee e left join fetch e.reportingManager join fetch r.leaveType t left join fetch r.company where r.id = :id")
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
