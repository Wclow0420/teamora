package com.teamora.employee;

import com.teamora.common.exception.BadRequestException;
import com.teamora.employee.dto.EmployeeDtos.ChangeRoleRequest;
import com.teamora.employee.dto.EmployeeDtos.CreateEmployeeRequest;
import com.teamora.employee.dto.EmployeeDtos.ManagerOption;
import com.teamora.employee.dto.EmployeeDtos.UpdateEmployeeRequest;
import com.teamora.employee.dto.EmployeeResponse;
import com.teamora.security.CurrentEmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/employees")
@RequiredArgsConstructor
public class EmployeeController {

    private final EmployeeService employeeService;
    private final CurrentEmployeeService currentEmployee;

    /** Current signed-in employee's own profile. */
    @GetMapping("/me")
    public EmployeeResponse me() {
        return EmployeeResponse.from(currentEmployee.require());
    }

    /** Directory listing — management roles only, scoped to the caller's company. */
    @GetMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('OWNER','HR_ADMIN','MANAGER')")
    public List<EmployeeResponse> list(@RequestParam(required = false) String dept,
                                       @RequestParam(required = false) String q) {
        return employeeService.search(currentEmployee.require().getCompany().getId(), dept, q);
    }

    /** Employees assignable as a reporting manager (OWNER / HR_ADMIN / MANAGER). */
    @GetMapping("/managers")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('OWNER','HR_ADMIN')")
    public List<ManagerOption> managers() {
        return employeeService.assignableManagers(currentEmployee.require().getCompany().getId());
    }

    @GetMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('OWNER','HR_ADMIN','MANAGER')")
    public EmployeeResponse get(@PathVariable UUID id) {
        return employeeService.get(currentEmployee.require().getCompany().getId(), id);
    }

    /** Add an employee to the caller's company. */
    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('OWNER','HR_ADMIN')")
    public EmployeeResponse create(@Valid @RequestBody CreateEmployeeRequest req) {
        return employeeService.create(currentEmployee.require().getCompany(), req);
    }

    /** Change an employee's role. Owners cannot be demoted via this endpoint. */
    @PatchMapping("/{id}/role")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('OWNER','HR_ADMIN')")
    public EmployeeResponse changeRole(@PathVariable UUID id, @Valid @RequestBody ChangeRoleRequest req) {
        var me = currentEmployee.require();
        if (me.getId().equals(id)) {
            throw new BadRequestException("You cannot change your own role");
        }
        return employeeService.changeRole(me.getCompany().getId(), id, req);
    }

    /** Update an employee's profile (name / job title / department / role / reporting manager). */
    @PatchMapping("/{id}")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('OWNER','HR_ADMIN')")
    public EmployeeResponse update(@PathVariable UUID id, @Valid @RequestBody UpdateEmployeeRequest req) {
        return employeeService.update(currentEmployee.require(), id, req);
    }

    /** Hand over ownership to another employee — only the current owner may do this. */
    @PostMapping("/{id}/transfer-ownership")
    @org.springframework.security.access.prepost.PreAuthorize("hasRole('OWNER')")
    public EmployeeResponse transferOwnership(@PathVariable UUID id) {
        return employeeService.transferOwnership(currentEmployee.require(), id);
    }
}
