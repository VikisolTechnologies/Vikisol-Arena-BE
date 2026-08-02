package com.vikisol.arena.profile.dto;

import jakarta.validation.constraints.NotNull;

import java.util.List;

public record UpdateSkillsRequest(@NotNull(message = "is required") List<String> skills) {
}
