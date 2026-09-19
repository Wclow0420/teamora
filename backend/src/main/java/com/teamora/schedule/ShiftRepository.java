package com.teamora.schedule;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ShiftRepository extends JpaRepository<Shift, UUID> {

    // All shifts for the company across a date range, with the employee loaded.
    @Query("""
            select s from Shift s
            join fetch s.employee e
            where s.company.id = :companyId
              and s.workDate between :from and :to
            order by s.workDate, e.fullName
            """)
    List<Shift> findByCompanyIdAndWorkDateBetween(@Param("companyId") UUID companyId,
                                                  @Param("from") LocalDate from,
                                                  @Param("to") LocalDate to);

    // Existing shift for an employee on a date (for upsert).
    Optional<Shift> findByEmployeeIdAndWorkDate(UUID employeeId, LocalDate workDate);
}
