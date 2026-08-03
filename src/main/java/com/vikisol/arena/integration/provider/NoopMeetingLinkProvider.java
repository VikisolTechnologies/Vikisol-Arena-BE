package com.vikisol.arena.integration.provider;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

// Default MeetingLinkProvider until Microsoft Teams (or another provider) is configured - returns
// the exact same placeholder link arena-web's mock already generates on confirm
// (`https://meet.arena.dev/{id}` - see arena-web/src/lib/api/interviews.ts), so wiring this
// provider in at InterviewService.confirmSlot() is a non-breaking, behavior-preserving change: a
// deployment with zero integration keys configured looks identical to the mock it's replacing.
@Slf4j
@Component
public class NoopMeetingLinkProvider implements MeetingLinkProvider {

    @Override
    public String getProviderName() {
        return "None (placeholder link)";
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public String createMeetingLink(UUID interviewId, String subject, Instant startTime, int durationMinutes, List<String> attendeeEmails) {
        log.debug("No meeting-link provider configured - using placeholder link for interview {}", interviewId);
        return "https://meet.arena.dev/" + interviewId;
    }
}
