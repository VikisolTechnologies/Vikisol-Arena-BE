package com.vikisol.arena.audit;

public record AuditEventResponse(
        String id,
        String actorName,
        String action,
        String target,
        String metadata,
        String createdAt
) {
}
