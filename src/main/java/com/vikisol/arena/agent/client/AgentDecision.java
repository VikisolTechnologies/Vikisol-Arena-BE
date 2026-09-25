package com.vikisol.arena.agent.client;

import com.fasterxml.jackson.databind.JsonNode;

// UNKNOWN means execution may have happened; never present it as a safe retry.
public record AgentDecision(String status, JsonNode result, String error) {}
