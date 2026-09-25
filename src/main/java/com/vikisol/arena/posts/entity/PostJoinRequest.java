package com.vikisol.arena.posts.entity;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Independent join entity, same shape as marketplace's Bid (not a @OneToMany collection on
// Post) - one row per (post, user), @Enumerated status, no cascade. UNIQUE(post_id, user_id)
// at the DB level (see V4 migration) prevents duplicate join requests from the same user.
@Entity
@Table(name = "arena_post_joins", uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "user_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PostJoinRequest extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PostJoinStatus status = PostJoinStatus.PENDING;

    private Instant decidedAt;

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private PostJoinOutcome outcome;
}
