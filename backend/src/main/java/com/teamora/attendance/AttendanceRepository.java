package com.teamora.attendance;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface AttendanceRepository extends JpaRepository<AttendanceRecord, UUID> {

    Optional<AttendanceRecord> findByEmployeeIdAndWorkDate(UUID employeeId, LocalDate workDate);

    @Query("""
            select r from AttendanceRecord r
            where r.employee.id = :employeeId
              and r.workDate between :from and :to
            order by r.workDate desc
            """)
    List<AttendanceRecord> findByEmployeeInRange(@Param("employeeId") UUID employeeId,
                                                 @Param("from") LocalDate from,
                                                 @Param("to") LocalDate to);

    /** A company's records for a given day, with employee eagerly fetched (admin live view). */
    @Query("""
            select r from AttendanceRecord r
            join fetch r.employee e
            where r.company.id = :companyId
              and r.workDate = :workDate
            order by e.fullName asc
            """)
    List<AttendanceRecord> findAllByWorkDateWithEmployee(@Param("companyId") UUID companyId,
                                                         @Param("workDate") LocalDate workDate);

    /**
     * A company's records across a date range (dashboard week chart). Employee is
     * not fetched — only its id/status/date are read.
     */
    @Query("""
            select r from AttendanceRecord r
            where r.company.id = :companyId
              and r.workDate between :from and :to
            """)
    List<AttendanceRecord> findAllByWorkDateRange(@Param("companyId") UUID companyId,
                                                  @Param("from") LocalDate from,
                                                  @Param("to") LocalDate to);
}
