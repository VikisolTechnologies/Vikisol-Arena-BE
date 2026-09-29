package com.vikisol.arena.activities.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// Request and response bodies for /activities (G7-G13, reshaped to ARENA-APP-FLOW §3 and
// FE-API-GAPS rows 7, 8, 23, 25). Grouped in one file: each is a few fields.
public final class ActivityDtos {

    private ActivityDtos() {
    }

    // --- responses ---

    // GET /activities/{id}. `viewer` is absent for a guest.
    public record ActivityResponse(
            String postId,
            String category,
            String subtype,
            String level,
            Cost cost,
            Map<String, Object> typeAnswers,
            List<String> bring,
            String accessibility,
            Boolean indoor,
            Integer minSize,
            boolean waitlist,
            String repeat,
            boolean womenOnly,
            String reach,
            String coverUrl,
            boolean needsEmergencyContact,
            List<QuestionResponse> questions,
            Integer spotsLeft,
            long waitlistCount,
            ViewerState viewer
    ) {
    }

    // type: "free" | "shared"; perPersonInr only for shared.
    public record Cost(String type, Integer perPersonInr, String note) {
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
            Boolean attendedConfirmed,
            String disputeStatus,
            String disputeOpenUntil,
            List<Integer> reminders
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
            Boolean joinerAttended,
            String disputeStatus,
            String disputeReason
    ) {
    }

    public record EmergencyContactResponse(String userId, String name, String contactName, String contactPhone) {
    }

    public record FeedbackResponse(String id, String postId, String activity, String fromUserId, String fromName,
                                   Boolean joinAgain, String note, String createdAt) {
    }

    // --- requests ---

    // PUT /activities/{id}/details. Only the fields sent change; category + subtype are required
    // the first time.
    public record UpdateDetailsRequest(
            String category,
            @Size(max = 40, message = "must be at most 40 characters") String subtype,
            String level,
            @Valid CostInput cost,
            Map<String, Object> typeAnswers,
            List<String> bring,
            @Size(max = 300, message = "must be at most 300 characters") String accessibility,
            Boolean indoor,
            @Min(value = 1, message = "must be at least 1") @Max(value = 500, message = "must be at most 500") Integer minSize,
            Boolean waitlist,
            String repeat,
            Boolean womenOnly,
            String reach
    ) {
    }

    public record CostInput(
            @NotBlank(message = "is required") String type,
            @Min(value = 1, message = "must be at least 1") @Max(value = 100000, message = "must be at most 100000") Integer perPersonInr,
            @Size(max = 200, message = "must be at most 200 characters") String note
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

    public record EmergencyContactInput(
            @NotBlank(message = "is required") @Size(max = 80, message = "must be at most 80 characters") String name,
            @NotBlank(message = "is required") @Pattern(regexp = "\\+?[0-9 ()-]{6,20}", message = "must be a phone number") String phone
    ) {
    }

    // POST /activities/{id}/join and POST /activities/{id}/waitlist.
    public record JoinRequest(
            List<@Valid AnswerInput> answers,
            @Size(max = 280, message = "must be at most 280 characters") String note,
            @Valid EmergencyContactInput emergencyContact
    ) {
    }

    public record DisputeRequest(
            @NotBlank(message = "is required") @Size(max = 500, message = "must be at most 500 characters") String reason
    ) {
    }

    // Row 25: the joiner's attendance confirmation; a dispute text is required when they say
    // they came but the host marked them absent.
    public record ConfirmAttendanceRequest(
            @NotNull(message = "is required") Boolean attended,
            @Size(max = 500, message = "must be at most 500 characters") String dispute
    ) {
    }

    // Flow §3 A14. toUserId is the host by default (a joiner's feedback); a host names the joiner.
    public record FeedbackRequest(
            UUID toUserId,
            @NotNull(message = "is required") Boolean joinAgain,
            @Size(max = 500, message = "must be at most 500 characters") String note
    ) {
    }

    public record ReminderRequest(
            @NotNull(message = "is required") @Min(value = 5, message = "must be at least 5")
            @Max(value = 10080, message = "must be at most 10080 (a week)") Integer minutesBefore
    ) {
    }
}
