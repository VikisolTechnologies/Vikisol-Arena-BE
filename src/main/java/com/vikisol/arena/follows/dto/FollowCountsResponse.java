package com.vikisol.arena.follows.dto;

public record FollowCountsResponse(
        String userId,
        long followerCount,
        long followingCount,
        Boolean viewerFollows
) {
}
