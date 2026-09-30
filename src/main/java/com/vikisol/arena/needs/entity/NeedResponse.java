package com.vikisol.arena.needs.entity;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.entity.BaseEntity;
import com.vikisol.arena.messaging.entity.Conversation;
import com.vikisol.arena.posts.entity.Post;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Someone answering a need ("I can help") or an offer ("I'd like this"), with a short message
// (G15). Accepting it opens the pair's private conversation.
@Entity
@Table(name = "arena_post_responses", uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "user_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class NeedResponse extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false, length = 500)
    private String message;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private ResponseStatus status = ResponseStatus.PENDING;

    private Instant decidedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id")
    private Conversation conversation;
}
