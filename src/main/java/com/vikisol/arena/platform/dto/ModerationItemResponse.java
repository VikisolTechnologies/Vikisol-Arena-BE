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
        String postId,
        // Row 15 (added): the evidence files the reporter attached (signed links).
        java.util.List<String> evidenceUrls,
        // Row 61 (added): the person a "user" report is about; null for other reports.
        String reportedUserId
) {
}
