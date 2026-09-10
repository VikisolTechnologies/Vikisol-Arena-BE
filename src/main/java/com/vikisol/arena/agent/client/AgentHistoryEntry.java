package com.vikisol.arena.agent.client;

// role is "user" or "agent" - deliberately a plain String rather than the JPA AgentMessageRole
// enum, so this client-boundary package has no dependency on the persistence layer.
public record AgentHistoryEntry(String role, String content) {}
