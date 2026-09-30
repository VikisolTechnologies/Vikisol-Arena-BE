package com.vikisol.arena.posts.dto;

import jakarta.validation.constraints.Size;

import java.util.List;

// FE-API-GAPS rows 14, 39, flow A10: the owner edits an open post. Every field is optional and
// only changes when sent; an empty string clears a nullable field (not body).
public record UpdatePostRequest(
        @Size(max = 200, message = "must be at most 200 characters") String title,
        @Size(max = 10000, message = "must be at most 10000 characters") String body,
        String startsAt,
        String endsAt,
        @Size(max = 200, message = "must be at most 200 characters") String locationText,
        @Size(max = 500, message = "must be at most 500 characters") String exactMeetingPoint,
        @Size(max = 20, message = "can have at most 20 tags") List<@Size(max = 40, message = "must be at most 40 characters") String> tags
) {
}
