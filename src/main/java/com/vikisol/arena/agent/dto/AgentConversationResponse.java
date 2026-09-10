package com.vikisol.arena.agent.dto;

import java.time.Instant;
import java.util.UUID;

public record AgentConversationResponse(
        UUID id,
        String title,
        Instant createdAt,
        Instant updatedAt
) {}
