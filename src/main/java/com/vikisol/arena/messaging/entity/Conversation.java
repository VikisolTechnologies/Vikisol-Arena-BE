package com.vikisol.arena.messaging.entity;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// One shared row per pair of users (not one localStorage list per browser like the mock) so both
// sides of a conversation genuinely see the same thread - a real requirement once this sits
// behind two different logged-in accounts instead of one browser's localStorage. Per-side read
// state (lastReadAtA/B) is what "unread" is computed from for whichever side is viewing.
@Entity
@Table(name = "arena_conversations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Conversation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_a_id", nullable = false)
    private User userA;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_b_id", nullable = false)
    private User userB;

    private String context;

    @Column(nullable = false)
    private Instant lastMessageAt;

    private Instant lastReadAtA;
    private Instant lastReadAtB;
}
