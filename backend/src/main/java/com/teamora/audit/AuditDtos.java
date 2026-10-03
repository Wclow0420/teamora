package com.teamora.audit;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

public final class AuditDtos {

    private AuditDtos() {}

    /** One audit-trail row; names are resolved for display (null if that person is gone). */
    public record AuditEventResponse(
            UUID id,
            AuditAction action,
            UUID actorId,
            String actorName,
            UUID targetEmployeeId,
            String targetName,
            Map<String, Object> details,
            Instant createdAt
    ) {}
}
