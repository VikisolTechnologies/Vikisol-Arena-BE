package com.vikisol.arena.activities.entity;

import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import com.vikisol.arena.posts.entity.PostJoinRequest;

import java.time.Instant;

// Check-in and the attendance dispute for one approved join (G10, G11). The host's outcome itself
// stays on PostJoinRequest.outcome; this records when it was set, so the 72h window can run.
@Entity
@Table(name = "arena_activity_attendance")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ActivityAttendance extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "join_id", nullable = false, unique = true)
    private PostJoinRequest joinRequest;

    private Instant checkedInAt;

    private Instant outcomeRecordedAt;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private DisputeStatus disputeStatus = DisputeStatus.NONE;

    @Column(length = 500)
    private String disputeReason;

    private Instant disputedAt;

    private Instant disputeResolvedAt;

    // Flow §3 A13 / row 25: the joiner's own confirmation after the activity.
    private Boolean joinerAttended;

    private Instant joinerConfirmedAt;

    // Flow §9 disputes queue (V37): the admin's note and who decided. A host accepting a dispute
    // leaves both empty.
    @Column(length = 500)
    private String disputeResolutionNote;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "dispute_resolved_by_user_id")
    private com.vikisol.arena.auth.entity.User disputeResolvedBy;
}
