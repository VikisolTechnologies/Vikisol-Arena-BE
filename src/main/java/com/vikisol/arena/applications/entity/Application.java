package com.vikisol.arena.applications.entity;

import com.vikisol.arena.common.entity.BaseEntity;
import com.vikisol.arena.jobs.entity.JobPosting;
import com.vikisol.arena.profile.entity.CandidateProfile;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// The ONE entity backing both the candidate-facing `Application` view and the enterprise-facing
// `Applicant` view in types.ts - AUDIT.md explicitly flagged the mock frontend's two independently
// -seeded parallel lists as a data-coherence bug (a stage change on one side silently not
// reflecting on the other). A single row here means both views always agree.
@Entity
@Table(name = "arena_applications", uniqueConstraints = @UniqueConstraint(columnNames = {"candidate_id", "job_posting_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Application extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    private CandidateProfile candidate;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id", nullable = false)
    private JobPosting jobPosting;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ApplicationStage stage = ApplicationStage.APPLIED;

    @Column(nullable = false)
    private Instant appliedAt;

    // V29, FE-API-GAPS row 20: sent with the application.
    @Column(length = 2000)
    private String coverNote;

    // Row 20 / flow §6.4: the candidate ticked "include my CTC" for THIS application - the only
    // way an employer ever sees their pay (CompensationPolicy).
    @Column(nullable = false)
    @Builder.Default
    private boolean includeCtc = false;

    // Flow §6: a hire goes on the profile only if the person chooses.
    @Column(nullable = false)
    @Builder.Default
    private boolean showOutcome = false;
}
