package com.vikisol.arena.communities.dto;

import jakarta.validation.constraints.Size;

// Every field optional - only the ones sent change.
public record UpdateCommunityRequest(
        @Size(max = 500, message = "must be at most 500 characters") String description,
        @Size(max = 16) String emoji,
        Boolean allowAnonymous
) {
}
