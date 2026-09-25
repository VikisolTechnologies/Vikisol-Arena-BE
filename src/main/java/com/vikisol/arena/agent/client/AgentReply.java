package com.vikisol.arena.agent.client;

import com.fasterxml.jackson.databind.JsonNode;
import java.time.Instant;
import java.util.List;

public record AgentReply(String content, List<ProposedAction> pendingActions) {
    public AgentReply(String content) { this(content, List.of()); }
    public AgentReply { if (pendingActions == null) pendingActions = List.of(); }
    public record ProposedAction(String actionId, String toolName, JsonNode args, Instant expiresAt) {}
}
