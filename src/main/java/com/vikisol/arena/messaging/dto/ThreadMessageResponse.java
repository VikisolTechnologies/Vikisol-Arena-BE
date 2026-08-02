package com.vikisol.arena.messaging.dto;

// Field-for-field mirror of arena-web's `ThreadMessage` type. `fromMe` is resolved relative to
// whichever user made the request.
public record ThreadMessageResponse(
        String id,
        String conversationId,
        boolean fromMe,
        String content,
        String timestamp
) {
}
