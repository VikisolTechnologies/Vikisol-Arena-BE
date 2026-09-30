package com.vikisol.arena.profile.dto;

import java.util.List;

// FE-API-GAPS 1-5: the onboarding fields, returned by every /profile/me basics write so the
// screen can show what was saved. Self-only (intents never leave this response).
public record ProfileBasicsResponse(
        String name,
        String title,
        String bio,
        String photoUrl,
        List<String> intents,
        List<String> interests,
        List<String> availability
) {
}
