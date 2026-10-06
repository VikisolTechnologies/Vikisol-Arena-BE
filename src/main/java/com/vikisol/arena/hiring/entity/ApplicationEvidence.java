package com.vikisol.arena.hiring.entity;

import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import com.vikisol.arena.applications.entity.Application;
import com.vikisol.arena.auth.entity.User;

import java.time.Instant;

// One requirement on one application (G24, G26): what the candidate showed, and the recruiter's
// private assessment of it. The candidate never sees the assessment or the note.
@Entity
@Table(name = "arena_application_evidence", uniqueConstraints = @UniqueConstraint(columnNames = {"application_id", "requirement_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ApplicationEvidence extends BaseEntity {

    public enum Assessment { MET, PARTLY, NOT_MET, UNCLEAR }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "requirement_id", nullable = false)
    private JobRequirement requirement;

    @Column(length = 300)
    private String candidateEvidence;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Assessment assessment;

    @Column(length = 500)
    private String assessmentNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "assessed_by_user_id")
    private User assessedBy;

    private Instant assessedAt;
}
