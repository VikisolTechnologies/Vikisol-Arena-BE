package com.vikisol.arena.posts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreatePostRequest(
        @NotBlank(message = "is required") String intentType,
        @NotBlank(message = "is required") String body,
        String locationText,
        String audience,
        String visibility,
        @Positive(message = "must be positive") Integer capacity,
        String startsAt,
        String endsAt,
        List<String> tags,
        List<String> mediaUrls
) {
    public CreatePostRequest {
        if (tags == null) tags = List.of();
        if (mediaUrls == null) mediaUrls = List.of();
    }
}
