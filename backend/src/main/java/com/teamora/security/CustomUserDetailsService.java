package com.teamora.security;

import com.teamora.employee.Employee;
import com.teamora.employee.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;

/** Loads an {@link Employee} as a Spring Security principal keyed by email. */
@Service
@RequiredArgsConstructor
public class CustomUserDetailsService implements UserDetailsService {

    private final EmployeeRepository employees;

    @Override
    public UserDetails loadUserByUsername(String email) throws UsernameNotFoundException {
        Employee e = employees.findByEmailIgnoreCase(email)
                .orElseThrow(() -> new UsernameNotFoundException("No account for " + email));
        return User.withUsername(e.getEmail())
                .password(e.getPasswordHash())
                .authorities(List.of(new SimpleGrantedAuthority(e.getRole().authority())))
                .disabled(!e.isActive())
                .build();
    }
}
