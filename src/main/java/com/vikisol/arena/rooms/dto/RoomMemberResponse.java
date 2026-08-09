package com.vikisol.arena.rooms.dto;

public record RoomMemberResponse(
        String userId,
        String name,
        String emoji,
        String role
) {
}
