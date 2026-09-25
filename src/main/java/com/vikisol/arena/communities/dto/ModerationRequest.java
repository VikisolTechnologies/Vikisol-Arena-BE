package com.vikisol.arena.communities.dto;

import jakarta.validation.constraints.Size;

// Body for moderator actions: set a role, ban/unban, or remove a post (with an optional reason).
public record ModerationRequest(Boolean value, @Size(max = 200) String reason) {
}
