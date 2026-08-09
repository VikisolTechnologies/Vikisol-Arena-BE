package com.vikisol.arena.follows.entity;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

// ARENA-V2-PRODUCT-ARCHITECTURE.md §4 "report, block, and mute everywhere." Lives alongside
// Follow - both are directed user-to-user relationships, same independent-join-entity shape
// (mirrors Bid), not a collection on User. Phase B scope: blocking hides the blocked user's
// posts from your feed and prevents new join requests between the two of you in either
// direction - it does NOT retroactively purge an existing shared room (a documented,
// deliberate scope boundary, not an oversight - see DECISIONS.md).
@Entity
@Table(name = "arena_user_blocks", uniqueConstraints = @UniqueConstraint(columnNames = {"blocker_user_id", "blocked_user_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UserBlock extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocker_user_id", nullable = false)
    private User blockerUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "blocked_user_id", nullable = false)
    private User blockedUser;
}
