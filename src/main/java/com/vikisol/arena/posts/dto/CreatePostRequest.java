package com.vikisol.arena.posts.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

import java.util.List;

public record CreatePostRequest(
        @NotBlank(message = "is required") String intentType,
        String title,
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
        String requiredVerificationLevel,
        // Phase 2 (Discuss) - post a question/update into this community (id); null = general.
        String communityId,
        // Phase 2 part C - show under an alias instead of the author's name (questions/updates only).
        Boolean anonymous,
        // Optional extras (FE-API-GAPS row 23): an activity's structure and up to three host
        // questions, saved with the post in one step. Jenny's createPost never sends them.
        @jakarta.validation.Valid com.vikisol.arena.activities.dto.ActivityDtos.UpdateDetailsRequest activity,
        List<@jakarta.validation.constraints.Size(max = 200, message = "must be at most 200 characters") String> hostQuestions,
        // Optional extra (row 27): a need's or offer's intake, saved with the post in one step.
        @jakarta.validation.Valid com.vikisol.arena.needs.dto.NeedDtos.DetailsRequest need
) {
    public CreatePostRequest {
        if (tags == null) tags = List.of();
        if (mediaUrls == null) mediaUrls = List.of();
    }

    // The shape before the row 23 extras, for existing callers.
    public CreatePostRequest(String intentType, String title, String body, String locationText, String audience, String visibility,
                             Integer capacity, String startsAt, String endsAt, List<String> tags, List<String> mediaUrls,
                             Double lat, Double lng, String exactMeetingPoint, String requiredVerificationLevel, String communityId,
                             Boolean anonymous) {
        this(intentType, title, body, locationText, audience, visibility, capacity, startsAt, endsAt, tags, mediaUrls, lat, lng,
                exactMeetingPoint, requiredVerificationLevel, communityId, anonymous, null, null, null);
    }
}
