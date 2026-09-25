package com.vikisol.arena.communities.dto;

// viewerRole: "owner" | "moderator" | "member" | null (not joined / signed out).
public record CommunityResponse(
        String id,
        String slug,
        String name,
        String description,
        String emoji,
        long memberCount,
        long postCount,
        boolean allowAnonymous,
        String viewerRole,
        boolean viewerBanned,
        String createdAt,
        boolean demoContent
) {
}
