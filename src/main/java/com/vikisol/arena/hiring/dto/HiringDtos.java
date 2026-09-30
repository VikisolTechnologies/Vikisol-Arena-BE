package com.vikisol.arena.hiring.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.Map;
import java.util.UUID;

// Bodies for G22-G26. Evidence is counted, never scored: "N of M must-haves", no percentages.
public final class HiringDtos {

    private HiringDtos() {
    }

    public record RequirementsRequest(
            @NotNull(message = "is required") List<String> mustHaves,
            List<String> niceToHaves
    ) {
    }

    // type (rows 20/28, optional, default text): text | yesno | number | choice. A choice
    // question needs 2-8 options (each ≤80).
    public record QuestionInput(
            @NotBlank(message = "is required") @Size(max = 200, message = "must be at most 200 characters") String text,
            Boolean required,
            String type,
            List<String> options
    ) {
        public QuestionInput(String text, Boolean required) {
            this(text, required, null, null);
        }
    }

    public record ScreeningRequest(@NotNull(message = "is required") List<@Valid QuestionInput> questions) {
    }

    public record Item(String id, String text) {
    }

    // type and options (added, rows 20/28); options only on a choice question.
    public record QuestionView(String id, String text, boolean required, String type, List<String> options) {
    }

    // GET /jobs/{id}/questions, in the frontend's field shape (row 20).
    public record JobQuestion(String id, String type, String label, List<String> options, boolean required) {
    }

    // GET /jobs/{id}/requirements - what a candidate sees before applying.
    public record JobRequirementsView(List<Item> mustHaves, List<Item> niceToHaves, List<QuestionView> screeningQuestions) {
    }

    public record AnswerInput(@NotNull(message = "is required") UUID questionId,
                              @Size(max = 1000, message = "must be at most 1000 characters") String answer) {
    }

    public record EvidenceInput(@NotNull(message = "is required") UUID requirementId,
                                @Size(max = 300, message = "must be at most 300 characters") String evidence) {
    }

    // PUT /applications/{id}/screening
    public record ScreeningAnswersRequest(List<@Valid AnswerInput> answers, List<@Valid EvidenceInput> evidence) {
    }

    // The candidate's own view: their answers and evidence, never the recruiter's assessment.
    public record CandidateScreeningView(
            List<AnswerView> answers,
            List<EvidenceView> evidence,
            int requiredUnanswered
    ) {
    }

    public record AnswerView(String questionId, String question, boolean required, String answer) {
    }

    public record EvidenceView(String requirementId, String kind, String text, String candidateEvidence) {
    }

    // Recruiter's checklist row: the candidate's evidence plus the team's private assessment.
    public record ChecklistItem(String requirementId, String kind, String text, String candidateEvidence,
                                String source, String assessment, String note) {
    }

    public record EvidenceSummary(int mustHaves, int withEvidence, int met) {
    }

    public record ApplicantEvidenceView(
            String applicationId,
            String stage,
            List<ChecklistItem> checklist,
            List<AnswerView> answers,
            EvidenceSummary summary
    ) {
    }

    public record ApplicantEvidenceRow(String applicationId, String candidateId, String name, String stage, EvidenceSummary summary) {
    }

    public record AssessmentRequest(
            @NotBlank(message = "is required") String assessment,
            @Size(max = 500, message = "must be at most 500 characters") String note
    ) {
    }

    // G25: counts per stage for the Manage-job funnel.
    public record FunnelView(Map<String, Long> stages, long total) {
    }
}
