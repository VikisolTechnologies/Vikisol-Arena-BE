package com.vikisol.arena.marketplace.entity;

import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "arena_milestones")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Milestone extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "project_id", nullable = false)
    private Project project;

    @Column(nullable = false)
    private String label;

    @Column(nullable = false)
    private int orderIndex;

    // Mirrors arena-web's `Milestone.amount` (types.ts) - this tranche's share of the awarded
    // bid, set once at award time in ProjectService.award() (30/40/30 split, see
    // awardProject()/MILESTONE_SPLIT in myProjects.ts) and never changed afterwards. Added to an
    // existing table after milestones already existed in this DB, so it needs a default for
    // pre-existing rows - `columnDefinition` (same tool the codebase already reaches for on TEXT
    // columns elsewhere, e.g. Notification.body) gives `ddl-auto: update` a DEFAULT 0 to apply
    // rather than failing the ALTER TABLE against already-seeded arena_milestones rows.
    @Column(nullable = false, columnDefinition = "integer not null default 0")
    @Builder.Default
    private int amount = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MilestoneStatus status = MilestoneStatus.PENDING;
}
