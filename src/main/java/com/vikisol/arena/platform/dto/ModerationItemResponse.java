package com.vikisol.arena.platform.dto;

public record ModerationItemResponse(
        String id,
        String postingId,
        String postingTitle,
        String tenantName,
        String reason,
        String status,
        String createdAt
) {
}
