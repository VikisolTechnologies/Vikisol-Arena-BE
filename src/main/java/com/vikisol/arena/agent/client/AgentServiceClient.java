package com.vikisol.arena.agent.client;

import java.util.List;

/**
 * The one boundary Arena's own code is allowed to know about when talking to "the AI agent."
 * Nothing in {@code com.vikisol.arena.agent} - the controller, the orchestration service, the
 * frontend contract - depends on which concrete agent backend answers on the other side of this
 * interface, matching the interface -&gt; Noop -&gt; real pattern already used for
 * Email/WhatsApp/MeetingLink/PhoneOtp in {@code integration.provider} (see
 * {@code IntegrationProviderConfig}).
 * <p>
 * There is deliberately no {@code RealAgentServiceClient} in this codebase yet. Vikisol has a
 * separate, real AI project ("Jennysol", found on disk during the ARENA-CONTINUATION-REBUILD /
 * ARENA-DOCUMENT-3 investigation) - but as inspected, it is a single-user personal assistant
 * (calendar/email/weather/web-search tools, password-only auth for one person, SQLite, runs only
 * on localhost, never deployed) with zero Arena or HRLMS awareness and no multi-tenant identity
 * model. Wiring Arena directly to its current `/chat/stream` endpoint would produce exactly the
 * kind of fabrication these documents prohibit: something that talks fluently but cannot actually
 * search a job, read a candidate, or apply to anything, presented to users as "the real agent."
 * <p>
 * So this boundary exists and is wired to {@link NoopAgentServiceClient} today (see
 * {@code AgentProviderConfig}), giving the frontend an honest "unavailable" state instead of a
 * keyword-matcher pretending to be AI. A real implementation becomes possible once either (a)
 * Jennysol grows an Arena/HRLMS-aware ToolProvider set plus service-to-service auth Arena can
 * call under a real user's identity, or (b) a different, purpose-built agent backend is stood up
 * against this same interface - whichever the product owner decides. Either way, the rest of this
 * package does not change.
 */
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
}
