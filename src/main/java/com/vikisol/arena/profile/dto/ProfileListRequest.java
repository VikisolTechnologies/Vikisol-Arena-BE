package com.vikisol.arena.profile.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

// PUT /profile/me/intents {"intents": [...]} and PUT /profile/me/interests {"interests": [...]}.
public final class ProfileListRequest {

    private ProfileListRequest() {
    }

    public record Intents(@NotNull(message = "is required") List<String> intents) {
    }

    public record Interests(@NotNull(message = "is required") List<String> interests) {
    }
}
