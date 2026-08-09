package com.vikisol.arena.rooms.dto;

public record RoomResponse(
        String id,
        String postId,
        String postBody,
        String postIntentType,
        int memberCount,
        boolean unread,
        boolean muted,
        String postStatus,
        String lastMessageAt,
        String lastMessagePreview
) {
}
