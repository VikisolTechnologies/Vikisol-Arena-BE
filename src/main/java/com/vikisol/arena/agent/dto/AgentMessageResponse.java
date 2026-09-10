package com.vikisol.arena.agent.dto;

import java.time.Instant;
import java.util.UUID;

public record AgentMessageResponse(
        UUID id,
        String role,
        String content,
        boolean serviceUnavailable,
        Instant createdAt
) {}
