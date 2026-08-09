package com.vikisol.arena.rooms.dto;

public record RoomMessageResponse(
        String id,
        String roomId,
        String senderUserId,
        String senderName,
        String senderEmoji,
        boolean fromMe,
        String content,
        String createdAt
) {
}
