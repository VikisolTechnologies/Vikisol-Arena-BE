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
            Viewer viewer
    ) {
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
    public record OutcomeView(String postId, String kind, String category, String role, String completedAt) {
    }

    // "My offers" on Work.
    public record MyResponseView(String postId, String postTitle, String kind, ResponseView response) {
    }

    public record DetailsRequest(
            @NotBlank(message = "is required") String category,
            @Size(max = 100, message = "must be at most 100 characters") String preferredTime
    ) {
    }

    public record RespondRequest(
            @NotBlank(message = "is required") @Size(max = 500, message = "must be at most 500 characters") String message
    ) {
    }

    public record ConfirmRequest(@Size(max = 500, message = "must be at most 500 characters") String note) {
    }
}
