package com.vikisol.arena.integration.provider;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Creates the join link populated onto {@code Interview.meetingLink} when a slot is confirmed -
 * mirrors the shape of HRLMS-BE's {@code MeetingProvider} (interface -> Noop -> real Graph
 * implementation), simplified to a single string return since Arena's {@code Interview} entity
 * only ever persists a plain URL (see {@code arena-web}'s {@code Interview.meetingLink} contract
 * and {@code MeetingEmbed} component, which render it as-is regardless of what actually produced
 * it), not HRLMS-BE's richer calendar-event id/meeting-id/join-url triple.
 */
public interface MeetingLinkProvider {

    String getProviderName();

    boolean isConfigured();

    /**
     * @param interviewId the Arena interview this link is for (used by the Noop fallback to build
     *                     a stable placeholder link, and by the real provider for logging)
     * @param subject meeting title
     * @param startTime confirmed slot start
     * @param durationMinutes confirmed slot duration
     * @param attendeeEmails candidate + enterprise user emails to invite
     * @return a join URL, never null
     */
    String createMeetingLink(UUID interviewId, String subject, Instant startTime, int durationMinutes, List<String> attendeeEmails);
}
