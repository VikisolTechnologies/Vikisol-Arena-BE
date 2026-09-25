package com.vikisol.arena.agent.client;

import java.util.UUID;

// What a real AgentServiceClient implementation gets to identify the caller - deliberately not
// the raw User entity. ARENA-DOCUMENT-3 §10/§13: the agent backend must derive identity/role from
// the authenticated session and never accept a client-asserted userId/tenantId, and must only ever
// receive the minimal fields a tool call needs, not the full user record (password hash, tokens,
// etc. never leave this boundary).
public record AgentContext(UUID userId, String role, String context) {
    public AgentContext(UUID userId, String role) { this(userId, role, null); }
}
