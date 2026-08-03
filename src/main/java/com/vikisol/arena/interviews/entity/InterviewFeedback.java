package com.vikisol.arena.interviews.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

// Mirrors arena-web's `InterviewFeedback` type (rating/strengths/concerns/recommendation/
// submittedAt) - embedded directly on Interview (same "no separate table for a single nested
// value object" call as the codebase makes elsewhere) rather than InterviewSlot's pattern of a
// full related @Entity, since unlike proposedSlots this is a 0-or-1 value, not a list.
@Embeddable
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class InterviewFeedback {

    private Integer rating;

    @Column(columnDefinition = "TEXT")
    private String strengths;

    @Column(columnDefinition = "TEXT")
    private String concerns;

    @Enumerated(EnumType.STRING)
    private InterviewRecommendation recommendation;

    private Instant submittedAt;
}
