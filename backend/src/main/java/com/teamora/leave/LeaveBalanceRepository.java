package com.teamora.leave;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, UUID> {

    @Query("""
            select b from LeaveBalance b
            join fetch b.leaveType t
            where b.employee.id = :employeeId
            order by t.sortOrder asc, t.name asc
            """)
    List<LeaveBalance> findByEmployeeId(@Param("employeeId") UUID employeeId);

    Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeId(UUID employeeId, UUID leaveTypeId);
}
