package com.vikisol.arena.communities.dto;

public record CommunityMemberResponse(String userId, String name, String emoji, String role, boolean banned) {
}
