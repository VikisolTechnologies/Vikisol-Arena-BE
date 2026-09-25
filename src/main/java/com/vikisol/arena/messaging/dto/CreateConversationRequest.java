package com.vikisol.arena.messaging.dto;

import jakarta.validation.constraints.NotBlank;

// Either participantUserId (message a person) or postId (message a post's author - works for an
// anonymous post without revealing them). anonymous = hide yourself from the other person.
public record CreateConversationRequest(String participantUserId, String context, String postId, Boolean anonymous) {
}
