package com.vikisol.arena.platform.entity;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.entity.BaseEntity;
import com.vikisol.arena.jobs.entity.JobPosting;
import com.vikisol.arena.rooms.entity.Room;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.EqualsAndHashCode;
import lombok.NoArgsConstructor;

import java.time.Instant;

// PA4 (moderation queue). Two sources now: JobPostingService's auto-flag (banned-phrase scan
// at posting-creation time) and RoomService.report() (a real user report, ARENA-V2-PRODUCT-
// ARCHITECTURE.md §4). Generalized ADDITIVELY rather than restructured into a single generic
// contentType/contentId pair - see DECISIONS.md for why: jobPosting stays exactly as it was
// (now nullable, since a ROOM-type item has none), room is the new nullable counterpart, and
// contentType says which one is actually populated for a given row.
@Entity
@Table(name = "arena_moderation_items")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ModerationItem extends BaseEntity {

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(255) not null default 'JOB_POSTING'")
    @Builder.Default
    private ModerationContentType contentType = ModerationContentType.JOB_POSTING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "job_posting_id")
    private JobPosting jobPosting;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id")
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_user_id")
    private User reporter;

    @Column(nullable = false)
    private String reason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private ModerationStatus status = ModerationStatus.PENDING;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "resolved_by_user_id")
    private User resolvedBy;

    private Instant resolvedAt;
}
