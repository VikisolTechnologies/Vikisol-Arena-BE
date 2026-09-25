package com.vikisol.arena.agent.dto;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.UUID;

public record AgentActionResponse(UUID id, String toolName, JsonNode args, String status,
                                  JsonNode result, String error, Instant expiresAt) {}
