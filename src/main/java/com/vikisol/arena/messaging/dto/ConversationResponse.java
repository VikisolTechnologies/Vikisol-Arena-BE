package com.vikisol.arena.messaging.dto;

// Field-for-field mirror of arena-web's `Conversation` type. `participantId` is the other
// participant's real account id (see README decisions - the mock's participantId was a loosely
// typed display id; a shared backend conversation needs a real id to route replies to).
public record ConversationResponse(
        String id,
        String participantId,
        String participantName,
        String participantEmoji,
        String context,
        String lastMessageAt,
        boolean unread
) {
}
