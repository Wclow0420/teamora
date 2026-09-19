package com.teamora.company;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface CompanyRepository extends JpaRepository<Company, UUID> {
    boolean existsBySlug(String slug);
    Optional<Company> findBySlug(String slug);
}
