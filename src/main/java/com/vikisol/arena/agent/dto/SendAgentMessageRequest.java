package com.vikisol.arena.agent.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record SendAgentMessageRequest(
        @NotBlank @Size(max = 4000) String content
) {}
