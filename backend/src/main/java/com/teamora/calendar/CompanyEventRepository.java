package com.teamora.calendar;

import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface CompanyEventRepository extends JpaRepository<CompanyEvent, UUID> {

    List<CompanyEvent> findByCompanyIdAndEventDateBetweenOrderByEventDateAsc(
            UUID companyId, LocalDate start, LocalDate end);

    List<CompanyEvent> findByCompanyIdAndEventDateGreaterThanEqualOrderByEventDateAsc(
            UUID companyId, LocalDate from);
}
