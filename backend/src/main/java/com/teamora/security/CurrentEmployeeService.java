package com.teamora.security;

import com.teamora.common.exception.ResourceNotFoundException;
import com.teamora.employee.Employee;
import com.teamora.employee.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

/** Resolves the authenticated {@link Employee} from the security context. */
@Component
@RequiredArgsConstructor
public class CurrentEmployeeService {

    private final EmployeeRepository employees;

    public Employee require() {
        var auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getName() == null) {
            throw new ResourceNotFoundException("No authenticated employee");
        }
        return employees.findByEmailWithCompany(auth.getName())
                .orElseThrow(() -> ResourceNotFoundException.of("Employee", auth.getName()));
    }
}
