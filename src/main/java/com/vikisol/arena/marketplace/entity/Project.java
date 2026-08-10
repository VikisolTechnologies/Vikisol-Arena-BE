package com.vikisol.arena.marketplace.entity;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "arena_projects")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Project extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posted_by_user_id", nullable = false)
    private User postedByUser;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false)
    private int budgetMin;

    @Column(nullable = false)
    private int budgetMax;

    @Column(nullable = false)
    private int durationWeeks;

    @ElementCollection
    @CollectionTable(name = "arena_project_skills", joinColumns = @JoinColumn(name = "project_id"))
    @Column(name = "skill")
    @Builder.Default
    private List<String> skills = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ProjectStatus status = ProjectStatus.OPEN;

    // See ProjectKind's own comment - additive, defaults every existing row to PROJECT (its
    // pre-v3 behavior) via the migration's column default.
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(255) not null default 'PROJECT'")
    @Builder.Default
    private ProjectKind kind = ProjectKind.PROJECT;

    @Column(nullable = false)
    private Instant endsAt;

    @Column(name = "awarded_bid_id")
    private java.util.UUID awardedBidId;
}
