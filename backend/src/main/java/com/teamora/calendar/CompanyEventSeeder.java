package com.teamora.calendar;

import com.teamora.config.TeamoraProperties;
import com.teamora.employee.Employee;
import com.teamora.employee.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.YearMonth;

/** Demo company calendar events for the current month so the grid shows dots. */
@Slf4j
@Component
@Order(8)
@RequiredArgsConstructor
public class CompanyEventSeeder implements CommandLineRunner {

    private final CompanyEventRepository events;
    private final EmployeeRepository employees;
    private final TeamoraProperties props;

    @Override
    @Transactional
    public void run(String... args) {
        if (!props.seed() || events.count() > 0) {
            return;
        }
        employees.findByEmailIgnoreCase("owner@lumi.com").ifPresent(owner -> {
            var company = owner.getCompany();
            YearMonth ym = YearMonth.now();
            int today = LocalDate.now().getDayOfMonth();

            // Spread across distinct days of the current month, clamped to valid days so
            // the grid always shows dots AND at least some events fall on/after today
            // (so "upcoming" is reliably non-empty regardless of the run date).
            events.save(event(owner, day(ym, today - 7), "Town hall · all staff", EventType.TOWNHALL, "3:00 PM"));
            events.save(event(owner, day(ym, today), "Public holiday · Awal Muharram", EventType.HOLIDAY, null));
            events.save(event(owner, day(ym, today + 5), "Nadia's birthday 🎂", EventType.BIRTHDAY, null));
            events.save(event(owner, day(ym, today + 12), "Product launch", EventType.EVENT, null));
            log.info("Seeded 4 company events for {}", company.getName());
        });
    }

    /** A day in the current month, clamped to [1, lengthOfMonth]. */
    private static LocalDate day(YearMonth ym, int dayOfMonth) {
        int clamped = Math.max(1, Math.min(dayOfMonth, ym.lengthOfMonth()));
        return ym.atDay(clamped);
    }

    private CompanyEvent event(Employee owner, LocalDate date, String title, EventType type, String timeLabel) {
        CompanyEvent e = CompanyEvent.builder()
                .title(title)
                .eventDate(date)
                .eventType(type)
                .timeLabel(timeLabel)
                .build();
        e.setCompany(owner.getCompany());
        return e;
    }
}
