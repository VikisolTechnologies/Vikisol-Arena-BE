package com.vikisol.arena.communities.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record CreateCommunityRequest(
        @NotBlank(message = "is required") @Size(min = 3, max = 60, message = "must be 3-60 characters") String name,
        @Size(max = 500, message = "must be at most 500 characters") String description,
        @Size(max = 16) String emoji,
        Boolean allowAnonymous
) {
}
