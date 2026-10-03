package com.teamora.audit;

import com.teamora.audit.AuditDtos.AuditEventResponse;
import com.teamora.common.exception.ResourceNotFoundException;
import com.teamora.employee.Employee;
import com.teamora.employee.EmployeeRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Writes and reads the audit trail. {@link #record} joins the caller's
 * transaction, so an audited change and its audit row commit (or roll back)
 * together. Never put a clear-text secret in {@code details} — use {@link #mask}.
 */
@Service
@RequiredArgsConstructor
public class AuditService {

    static final int DEFAULT_LIMIT = 100;
    static final int MAX_LIMIT = 500;

    private final AuditEventRepository events;
    private final EmployeeRepository employees;

    /** Record one action by {@code actor} (company taken from the actor) on {@code target} (nullable). */
    @Transactional
    public void record(Employee actor, Employee target, AuditAction action, Map<String, Object> details) {
        events.save(AuditEvent.builder()
                .companyId(actor.getCompany().getId())
                .actorId(actor.getId())
                .targetEmployeeId(target != null ? target.getId() : null)
                .action(action)
                .details(details == null ? null : new LinkedHashMap<>(details))
                .createdAt(Instant.now())
                .build());
    }

    /** Ordered details map that tolerates null values (Map.of doesn't). */
    public static Map<String, Object> details(Object... keyValues) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < keyValues.length; i += 2) {
            m.put(String.valueOf(keyValues[i]), keyValues[i + 1]);
        }
        return m;
    }

    /** "••••5678": only the last 4 characters of an account/ID number (null stays null). */
    public static String mask(String value) {
        if (value == null) {
            return null;
        }
        String compact = value.replaceAll("\\s", "");
        return compact.length() <= 4 ? "••••" : "••••" + compact.substring(compact.length() - 4);
    }

    /** Newest first, company-scoped; optionally only events about one employee of that company. */
    @Transactional(readOnly = true)
    public List<AuditEventResponse> list(UUID companyId, UUID employeeId, Integer limit) {
        int size = limit == null || limit <= 0 ? DEFAULT_LIMIT : Math.min(limit, MAX_LIMIT);
        PageRequest page = PageRequest.of(0, size);
        List<AuditEvent> rows;
        if (employeeId != null) {
            employees.findByIdAndCompanyId(employeeId, companyId)
                    .orElseThrow(() -> ResourceNotFoundException.of("Employee", employeeId));
            rows = events.findByCompanyIdAndTargetEmployeeIdOrderByCreatedAtDesc(companyId, employeeId, page);
        } else {
            rows = events.findByCompanyIdOrderByCreatedAtDesc(companyId, page);
        }
        Set<UUID> ids = new HashSet<>();
        for (AuditEvent e : rows) {
            if (e.getActorId() != null) ids.add(e.getActorId());
            if (e.getTargetEmployeeId() != null) ids.add(e.getTargetEmployeeId());
        }
        Map<UUID, String> names = employees.findAllById(ids).stream()
                .filter(emp -> emp.getCompany().getId().equals(companyId))
                .collect(Collectors.toMap(Employee::getId, Employee::getFullName, (a, b) -> a));
        Function<UUID, String> name = id -> id == null ? null : names.get(id);
        return rows.stream().map(e -> new AuditEventResponse(
                e.getId(), e.getAction(), e.getActorId(), name.apply(e.getActorId()),
                e.getTargetEmployeeId(), name.apply(e.getTargetEmployeeId()),
                e.getDetails(), e.getCreatedAt())).toList();
    }
}
