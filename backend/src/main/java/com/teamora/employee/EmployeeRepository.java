package com.teamora.employee;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface EmployeeRepository extends JpaRepository<Employee, UUID> {

    // Login is by email across all tenants → email is globally unique.
    Optional<Employee> findByEmailIgnoreCase(String email);

    // Fetches the company eagerly so callers can read it outside a transaction
    // (open-in-view is off).
    @Query("select e from Employee e join fetch e.company where lower(e.email) = lower(:email)")
    Optional<Employee> findByEmailWithCompany(@Param("email") String email);

    boolean existsByEmailIgnoreCase(String email);

    // Tenant-scoped lookups.
    Optional<Employee> findByIdAndCompanyId(UUID id, UUID companyId);

    long countByCompanyIdAndActiveTrue(UUID companyId);

    List<Employee> findByCompanyIdAndActiveTrue(UUID companyId);

    /** The company's single OWNER (guaranteed at most one by a partial unique index). */
    Optional<Employee> findByCompanyIdAndRole(UUID companyId, Role role);

    /** Assignable reporting managers. */
    List<Employee> findByCompanyIdAndRoleInAndActiveTrue(UUID companyId, Collection<Role> roles);

    // ---- Fetch-join variants so list/get can expose the reporting-manager name
    //      without N+1 or lazy-init issues. ----
    @Query("select e from Employee e join fetch e.company left join fetch e.reportingManager where e.id = :id and e.company.id = :companyId")
    Optional<Employee> findByIdAndCompanyIdWithManager(@Param("id") UUID id, @Param("companyId") UUID companyId);

    // Empty-string sentinels (not null) so Postgres types the params as text.
    @Query("""
            select e from Employee e
            join fetch e.company
            left join fetch e.reportingManager
            where e.company.id = :companyId
              and (:dept = '' or lower(e.department) = lower(:dept))
              and (:q = '' or lower(e.fullName) like lower(concat('%', :q, '%'))
                           or lower(e.staffId)  like lower(concat('%', :q, '%'))
                           or lower(e.jobTitle) like lower(concat('%', :q, '%')))
            order by e.fullName asc
            """)
    List<Employee> searchWithManager(@Param("companyId") UUID companyId, @Param("dept") String dept, @Param("q") String q);
}
