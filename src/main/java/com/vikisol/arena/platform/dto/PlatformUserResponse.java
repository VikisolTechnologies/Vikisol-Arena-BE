package com.vikisol.arena.platform.dto;

public record PlatformUserResponse(
        String id,
        String name,
        String email,
        String role,
        String tenantId,
        String tenantName,
        String createdAt
) {
}
