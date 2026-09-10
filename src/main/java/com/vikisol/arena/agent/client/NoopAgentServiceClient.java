package com.vikisol.arena.agent.client;

import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Default binding until a real agent backend exists - see {@link AgentServiceClient}'s class doc
 * for why there is no real implementation yet. {@code isAvailable()} always reports false so
 * {@code AgentService} never even attempts {@code sendMessage} in production; the method below
 * exists only to satisfy the interface and intentionally refuses to be called, so a future wiring
 * mistake (calling sendMessage without checking isAvailable first) fails loudly instead of quietly
 * returning a fabricated reply.
 */
@Component
public class NoopAgentServiceClient implements AgentServiceClient {

    @Override
    public boolean isAvailable() {
        return false;
    }

    @Override
    public AgentReply sendMessage(AgentContext context, List<AgentHistoryEntry> history, String message) {
        throw new UnsupportedOperationException(
                "NoopAgentServiceClient.sendMessage() should never be invoked - callers must check isAvailable() first");
    }
}
