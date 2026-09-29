package com.vikisol.arena.needs.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

// Bodies for /needs (G14-G17). "Need" covers both ASK posts (a need) and OFFER posts (an offer):
// a response to a need is an offer of help; a response to an offer is a request for it.
public final class NeedDtos {

    private NeedDtos() {
    }

    // GET /needs/{id}. responseCount counts live responses (not withdrawn). viewer is absent for
    // a guest.
    public record NeedView(
            String postId,
            String kind,
            String category,
            String preferredTime,
            String status,
            long responseCount,
            Viewer viewer,
            // Row 27 (added): the flow §4 intake. offer is only on an offer.
            String urgency,
            String helpType,
            java.util.Map<String, Object> answers,
            OfferView offer
    ) {
    }

    // limitReached: the owner has accepted as many requests as their limit allows right now.
    public record OfferView(java.util.List<String> days, String limit, String proofUrl, boolean limitReached) {
    }

    public record Viewer(boolean owner, ResponseView myResponse) {
    }

    // conversationId and completion are only ever shown to the two people involved.
    public record ResponseView(
            String id,
            String postId,
            String userId,
            String name,
            String avatarEmoji,
            String message,
            String status,
            String createdAt,
            String conversationId,
            CompletionView completion
    ) {
    }

    public record CompletionView(
            String ownerConfirmedAt,
            String responderConfirmedAt,
            String ownerNote,
            String responderNote,
            String completedAt
    ) {
    }

    // One confirmed outcome on someone's public profile: what and when, never with whom.
    // title (added, row 13): the post's title or the start of its text.
    public record OutcomeView(String postId, String kind, String category, String role, String completedAt, String title) {
    }

    // "My offers" on Work.
    public record MyResponseView(String postId, String postTitle, String kind, ResponseView response) {
    }

    public record DetailsRequest(
            @NotBlank(message = "is required") String category,
            @Size(max = 100, message = "must be at most 100 characters") String preferredTime,
            // Row 27 (optional extras): today | week | flexible; free | exchange | costs (needs only);
            // the category's intake answers.
            String urgency,
            String helpType,
            java.util.Map<String, Object> answers,
            // Offers only: weekdays | weekends | evenings; once-a-week | twice-a-week |
            // a-few-times-a-month | no-limit; a portfolio or proof link.
            @Size(max = 3, message = "can have at most 3 items") java.util.List<String> days,
            String limit,
            @Size(max = 500, message = "must be at most 500 characters")
            @jakarta.validation.constraints.Pattern(regexp = "https?://\\S+", message = "must be an http(s) link") String proofUrl
    ) {
        public DetailsRequest(String category, String preferredTime) {
            this(category, preferredTime, null, null, null, null, null, null);
        }
    }

    public record RespondRequest(
            @NotBlank(message = "is required") @Size(max = 500, message = "must be at most 500 characters") String message
    ) {
    }

    public record ConfirmRequest(@Size(max = 500, message = "must be at most 500 characters") String note) {
    }
}
