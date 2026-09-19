package com.teamora.company;

import com.teamora.company.dto.CompanyDtos.CompanyResponse;
import com.teamora.company.dto.CompanyDtos.UpdateCompanyRequest;
import com.teamora.security.CurrentEmployeeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/companies")
@RequiredArgsConstructor
public class CompanyController {

    private final CompanyService companyService;
    private final CurrentEmployeeService currentEmployee;

    /** The caller's own company. */
    @GetMapping("/me")
    public CompanyResponse me() {
        return CompanyResponse.from(currentEmployee.require().getCompany());
    }

    /** Update the company profile — owners / HR admins only. */
    @PatchMapping("/me")
    @PreAuthorize("hasAnyRole('OWNER','HR_ADMIN')")
    public CompanyResponse update(@Valid @RequestBody UpdateCompanyRequest req) {
        return CompanyResponse.from(companyService.update(currentEmployee.require().getCompany(), req));
    }
}
