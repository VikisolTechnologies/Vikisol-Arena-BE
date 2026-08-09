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
        String createdAt,
        // Phase B additions. approxLat/approxLng are ALREADY jittered for display (see
        // PostMapper) - never the stored value directly. exactMeetingPoint is only ever
        // non-null when the viewer is the author or an approved room member - see
        // PostMapper.toResponse's own comment.
        Double approxLat,
        Double approxLng,
        String exactMeetingPoint,
        String requiredVerificationLevel,
        // Phase C additions.
        long commentCount,
        long reactionCount,
        Boolean myReacted
) {
}
