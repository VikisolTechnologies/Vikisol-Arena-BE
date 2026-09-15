package com.vikisol.arena.posts.dto;

import java.util.List;

// Field-for-field mirror of arena-web's `Post` type.
public record PostResponse(
        String id,
        String authorUserId,
        String authorName,
        String authorEmoji,
        // Post-spec reconciliation addition - set only for intentType=company, lets the
        // frontend link the post's author straight to /companies/{id}.
        String authorCompanyId,
        String intentType,
        String title,
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
        Boolean myReacted,
        // §4 safety-audit additions - trust signals for whoever's about to meet this post's
        // author in person. authorJoinCount = how many other posts they've been APPROVED into
        // (a real participation track record); authorAccountAgeDays = how long they've had an
        // account (a fresh same-day account is a real risk signal).
        long authorJoinCount,
        long authorAccountAgeDays,
        // ARENA-WEB-AND-SEED.md Part 4.2 - "every seeded item carries a visible 'Demo content'
        // marker in the UI... it must be impossible to screenshot it and believe the network is
        // alive." Straight passthrough of BaseEntity.demoContent - see DemoContentService.
        // No `is` prefix - matches this record's own existing boolean field convention (joinable,
        // mine), which Jackson serializes as-is for records rather than stripping a get/is prefix.
        boolean demoContent
) {
}
