package com.vikisol.arena.interviews.entity;

import com.vikisol.arena.applications.entity.Application;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.BatchSize;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "arena_interviews")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Interview extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    private Application application;

    // EAGER (every response DTO needs it) but @BatchSize keeps a multi-row load (e.g.
    // getMyAssignedInterviews) from issuing one secondary select per interview - up to this many
    // interviews' proposedSlots get pulled in a single `where interview_id in (...)` query instead.
    @OneToMany(mappedBy = "interview", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @BatchSize(size = 25)
    @Builder.Default
    private List<InterviewSlot> proposedSlots = new ArrayList<>();

    private UUID confirmedSlotId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InterviewStatus status = InterviewStatus.PROPOSED;

    // Field-for-field mirror of arena-web's `Interview.meetingLink` (types.ts) - plain string today
    // so a future WebRTC/Daily.co/Zoom embed only changes what MeetingEmbed renders, not this
    // contract. Populated by MeetingLinkProvider when a slot is confirmed - see
    // InterviewService.confirmSlot(). Did not previously exist on this entity (the prior pass
    // never modeled it); added here since Phase 5's meeting-link integration needs somewhere real
    // to persist the result.
    private String meetingLink;

    // Mirrors arena-web's `Interview.notes` (types.ts) - free-form text either participant can
    // jot during the call (InterviewRoom.tsx's notes textarea isn't gated by canGiveFeedback, so
    // both the candidate and enterprise side may write here).
    @Column(columnDefinition = "TEXT")
    private String notes;

    // Mirrors arena-web's `Interview.feedback` (types.ts) - null until the enterprise side submits
    // structured post-interview feedback via InterviewService.submitFeedback(). All-null-columns
    // reads back as a null InterviewFeedback (standard JPA embeddable behavior), so no separate
    // presence flag is needed.
    @Embedded
    private InterviewFeedback feedback;

    // HM3: a recruiter/company_admin assigns a hiring manager when scheduling an interview.
    // Nullable - most interviews go through recruiter/company_admin alone, this is opt-in.
    // Drives HM4's isolation: a hiring_manager can only reach *this specific* interview, not the
    // tenant's whole pipeline (see InterviewService.assertParticipant()'s per-role branching).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assigned_hiring_manager_user_id")
    private User assignedHiringManager;
}
