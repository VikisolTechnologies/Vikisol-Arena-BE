package com.vikisol.arena.activity.dto;

// Field-for-field mirror of arena-web's `AgentActivityEvent` type.
public record ActivityEventResponse(
        String id,
        String type,
        String title,
        String description,
        String timestamp,
        String relatedJobId,
        String rationale,
        Boolean undoable
) {
}
