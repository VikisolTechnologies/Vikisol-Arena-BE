package com.vikisol.arena.rooms.dto;

public record RoomResponse(
        String id,
        String postId,
        String postBody,
        String postIntentType,
        int memberCount,
        boolean unread,
        String lastMessageAt,
        String lastMessagePreview
) {
}
