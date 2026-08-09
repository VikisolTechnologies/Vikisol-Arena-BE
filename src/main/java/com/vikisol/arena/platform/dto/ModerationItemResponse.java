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
        String createdAt,
        // Phase C safety-audit addition - populated only for contentType=POST, so the admin
        // queue and any deep link can point straight at /feed/{postId}.
        String postId
) {
}
