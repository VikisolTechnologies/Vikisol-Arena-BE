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

    // Phase 2 part C - anonymous chats. Each side can be hidden from the other; see V16 and
    // ConversationService. The post it started from (if any), and who closed it (if closed).
    // Explicit names: the naming strategy would otherwise map anonymousA -> "anonymousa" (the
    // same thing that made lastReadAtA "last_read_ata").
    @Column(name = "anonymous_a", nullable = false)
    @Builder.Default
    private boolean anonymousA = false;

    @Column(name = "anonymous_b", nullable = false)
    @Builder.Default
    private boolean anonymousB = false;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private com.vikisol.arena.posts.entity.Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "closed_by_user_id")
    private User closedBy;

    private Instant closedAt;

    public boolean isAnonymous() {
        return anonymousA || anonymousB;
    }
}
