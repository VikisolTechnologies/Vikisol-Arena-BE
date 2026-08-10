package com.vikisol.arena.feed.dto;

import java.util.List;

// ARENA-MASTER-ARCHITECTURE.md PART 6/7.5 - the unified `/feed` response. See DECISIONS.md
// ("Step 3: the feed unifies ... at the API response level"): this does NOT merge Post/
// JobPosting/Project into one table, it merges their already-real responses into one flat shape
// with an `itemType` discriminator, same "flat DTO, nullable per-variant fields" convention
// PostResponse itself already uses for its own Phase A/B/C additions. Field-for-field mirror of
// arena-web's `FeedItem` type.
public record FeedItemResponse(
        String id,
        // "job" | "project" | "freelance" | "activity" | "ask" | "update" | "company" - freelance
        // is a Project with kind=FREELANCE (see Project.kind), not a separate underlying table.
        String itemType,
        String authorUserId,
        String authorName,
        String authorEmoji,
        String authorCompanyId,
        String authorCompanyName,
        String authorCompanyEmoji,
        // Only Job/Project carry a distinct title today - Post itself has no title field yet
        // (ACTIVITY/ASK/UPDATE render from `body` alone, per the existing PostCard). Null for
        // those item types until PART 7.6's composer "title" field lands for every post type.
        String title,
        String body,
        String locationText,
        List<String> tags,
        List<String> mediaUrls,
        String status,
        String createdAt,

        // ACTIVITY/ASK/UPDATE/COMPANY fields (sourced from Post) - null for job/project.
        String visibility,
        Integer capacity,
        Integer spotsFilled,
        String startsAt,
        String endsAt,
        Boolean joinable,
        Boolean mine,
        String myJoinStatus,
        String roomId,
        Double approxLat,
        Double approxLng,
        Long commentCount,
        Long reactionCount,
        Boolean myReacted,

        // JOB-only fields.
        String employmentType,
        Boolean remote,
        Integer salaryMin,
        Integer salaryMax,

        // PROJECT/FREELANCE-only fields.
        Integer budgetMin,
        Integer budgetMax,
        Integer durationWeeks,
        Long bidCount
) {
}
