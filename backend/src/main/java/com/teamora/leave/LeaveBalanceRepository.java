package com.teamora.leave;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface LeaveBalanceRepository extends JpaRepository<LeaveBalance, UUID> {

    List<LeaveBalance> findByEmployeeId(UUID employeeId);

    Optional<LeaveBalance> findByEmployeeIdAndLeaveType(UUID employeeId, LeaveType leaveType);
}
