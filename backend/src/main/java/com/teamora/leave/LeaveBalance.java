package com.teamora.leave;

import com.teamora.common.TenantEntity;
import com.teamora.employee.Employee;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.util.UUID;

/** An employee's entitlement and consumption for a single {@link LeaveType}. */
@Getter
@Setter
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Entity
@Table(name = "leave_balances")
public class LeaveBalance extends TenantEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "employee_id", nullable = false)
    private Employee employee;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "leave_type_id", nullable = false)
    private LeaveType leaveType;

    @Column(nullable = false)
    private Integer entitled;

    @Column(nullable = false)
    private Integer used;

    /** Days still available. */
    public int remaining() {
        int e = entitled == null ? 0 : entitled;
        int u = used == null ? 0 : used;
        return e - u;
    }
}
