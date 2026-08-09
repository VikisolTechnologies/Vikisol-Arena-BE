package com.vikisol.arena.posts.dto;

import jakarta.validation.constraints.NotBlank;

import java.util.List;

// ARENA-V2-PRODUCT-ARCHITECTURE.md §3.5/§6 - deliberately simpler than CreatePostRequest: a
// COMPANY post is always GLOBAL/PUBLIC/OPEN (a company page's own news/hiring/culture update,
// not a joinable activity), so there's no audience/visibility/capacity/location/verification
// picker to expose. Same minimal shape as an UPDATE post.
public record CreateCompanyPostRequest(
        @NotBlank(message = "is required") String body,
        List<String> tags
) {
    public CreateCompanyPostRequest {
        if (tags == null) tags = List.of();
    }
}
