package com.vikisol.arena.agent.client;

import java.util.List;

/** Product boundary: Arena owns identity, history and action authorization; JennySol owns AI execution. */
public interface AgentServiceClient {

    /** Cheap, side-effect-free check the orchestration layer uses before every send - lets the
     * Noop implementation answer instantly without a network round trip, and lets a real
     * implementation report a genuine outage without the caller having to attempt a send first. */
    boolean isAvailable();

    /**
     * @param context the authenticated caller - never accept userId/role from the request body
     * @param history bounded prior turns for this conversation, oldest first
     * @param message the new user message
     */
    AgentReply sendMessage(AgentContext context, List<AgentHistoryEntry> history, String message);
    default AgentDecision decideAction(AgentContext context, String actionId, boolean approve) {
        throw new IllegalStateException("Agent actions are unavailable");
    }
}

