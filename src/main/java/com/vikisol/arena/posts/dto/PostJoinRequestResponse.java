package com.vikisol.arena.posts.dto;

public record PostJoinRequestResponse(
        String id,
        String postId,
        String userId,
        String userName,
        String userEmoji,
        String status,
        String createdAt,
        String outcome,
        // Row 23 (added): the joiner's note and the host's decision note. Only the two of them
        // ever receive this object.
        String note,
        String decisionNote
) {
}
