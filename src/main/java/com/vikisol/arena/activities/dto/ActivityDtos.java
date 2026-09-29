package com.vikisol.arena.activities.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// Request and response bodies for /activities (G7-G13). Grouped in one file: each is a few fields.
public final class ActivityDtos {

    private ActivityDtos() {
    }

    // --- responses ---

    // GET /activities/{id}. `viewer` is absent for a guest.
    public record ActivityResponse(
            String postId,
            String kind,
            Map<String, String> details,
            String coverUrl,
            boolean waitlistEnabled,
            List<QuestionResponse> questions,
            Integer spotsLeft,
            long waitlistCount,
            ViewerState viewer
    ) {
    }

    public record QuestionResponse(String id, String text, boolean required) {
    }

    // Everything about the signed-in person's own relationship to this activity. Attendance and
    // dispute fields are only ever the viewer's own.
    public record ViewerState(
            boolean host,
            String joinStatus,
            Integer waitlistPosition,
            boolean answered,
            String checkedInAt,
            String outcome,
            String disputeStatus,
            String disputeOpenUntil
    ) {
    }

    public record AnswerResponse(String questionId, String question, String answer) {
    }

    public record WaitlistEntryResponse(String userId, String name, String avatarEmoji, int position, String joinedAt) {
    }

    // Host-only attendance sheet row.
    public record AttendanceRow(
            String joinId,
            String userId,
            String name,
            String checkedInAt,
            String outcome,
            String outcomeRecordedAt,
            String disputeStatus,
            String disputeReason
    ) {
    }

    public record FeedbackResponse(String id, String postId, String activity, String fromUserId, String fromName,
                                   String text, String createdAt) {
    }

    // --- requests ---

    public record UpdateDetailsRequest(
            String kind,
            Map<String, String> details,
            Boolean waitlistEnabled
    ) {
    }

    public record QuestionInput(
            @NotBlank(message = "is required") @Size(max = 200, message = "must be at most 200 characters") String text,
            Boolean required
    ) {
    }

    public record SetQuestionsRequest(@NotNull(message = "is required") List<@Valid QuestionInput> questions) {
    }

    public record AnswerInput(
            @NotNull(message = "is required") UUID questionId,
            @Size(max = 500, message = "must be at most 500 characters") String answer
    ) {
    }

    // POST /activities/{id}/join and POST /activities/{id}/waitlist.
    public record JoinRequest(List<@Valid AnswerInput> answers) {
    }

    public record DisputeRequest(
            @NotBlank(message = "is required") @Size(max = 500, message = "must be at most 500 characters") String reason
    ) {
    }

    public record FeedbackRequest(
            @NotNull(message = "is required") UUID toUserId,
            @NotBlank(message = "is required") @Size(max = 500, message = "must be at most 500 characters") String text
    ) {
    }
}
