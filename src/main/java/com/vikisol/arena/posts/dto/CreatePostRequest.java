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
        List<String> mediaUrls,
        // Only used when the author explicitly taps "use my current location" in the composer
        // (its own in-the-moment browser Geolocation prompt, independent of account-wide
        // discovery consent) - immediately geohash-encoded and discarded server-side, same as
        // ProfileController's /me/location. Null = no location captured for this post.
        Double lat,
        Double lng,
        String exactMeetingPoint,
        String requiredVerificationLevel
) {
    public CreatePostRequest {
        if (tags == null) tags = List.of();
        if (mediaUrls == null) mediaUrls = List.of();
    }
}
