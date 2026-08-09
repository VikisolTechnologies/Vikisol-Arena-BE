package com.vikisol.arena.rooms.entity;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

// Phase A's safety-minimum guardrail for freeform-text meetup risk (ARENA-V2-PRODUCT-
// ARCHITECTURE.md §4 - full verification tiers/location jitter/moderation-queue wiring is
// explicitly Phase B). Just a durable record for now: no queue/admin-console UI reads this yet.
@Entity
@Table(name = "arena_room_reports")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RoomReport extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reporter_user_id", nullable = false)
    private User reporter;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String reason;
}
