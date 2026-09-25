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
        boolean unread,
        // Phase 2 part C. anonymous: the other person is hidden (participantId is then blank and
        // the name is an alias). meAnonymous: you are hidden from them. closed: no more messages.
        boolean anonymous,
        boolean meAnonymous,
        boolean closed,
        String postId
) {
}
