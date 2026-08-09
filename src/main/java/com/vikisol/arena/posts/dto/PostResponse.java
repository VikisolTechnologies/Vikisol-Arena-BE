package com.vikisol.arena.posts.dto;

import java.util.List;

// Field-for-field mirror of arena-web's `Post` type.
public record PostResponse(
        String id,
        String authorUserId,
        String authorName,
        String authorEmoji,
        String intentType,
        String body,
        String locationText,
        String audience,
        String visibility,
        Integer capacity,
        int spotsFilled,
        String status,
        String startsAt,
        String endsAt,
        List<String> tags,
        List<String> mediaUrls,
        boolean joinable,
        Boolean mine,
        String myJoinStatus,
        String roomId,
        String createdAt
) {
}
