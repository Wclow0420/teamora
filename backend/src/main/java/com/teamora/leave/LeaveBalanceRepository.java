package com.teamora.leave;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, UUID> {

    /** An employee's balances for one leave year, ordered like the leave-type catalogue. */
    @Query("""
            select b from LeaveBalance b
            join fetch b.leaveType t
            where b.employee.id = :employeeId and b.leaveYear = :leaveYear
            order by t.sortOrder asc, t.name asc
            """)
    List<LeaveBalance> findByEmployeeIdAndLeaveYear(@Param("employeeId") UUID employeeId,
                                                    @Param("leaveYear") int leaveYear);

    Optional<LeaveBalance> findByEmployeeIdAndLeaveTypeIdAndLeaveYear(UUID employeeId, UUID leaveTypeId, int leaveYear);
}
