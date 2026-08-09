package com.vikisol.arena.platform.dto;

public record ModerationItemResponse(
        String id,
        String contentType,
        String postingId,
        String postingTitle,
        String tenantName,
        String roomId,
        String reporterName,
        String reason,
        String status,
        String createdAt
) {
}
