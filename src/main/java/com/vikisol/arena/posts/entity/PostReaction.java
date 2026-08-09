package com.vikisol.arena.posts.entity;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

// ARENA-V2-PRODUCT-ARCHITECTURE.md Phase C. Independent join entity, same shape as
// Bid/PostJoinRequest/UserBlock/Follow - one row per (post, user), UNIQUE(post_id, user_id). A
// single like/emoji-style toggle rather than a multi-reaction-type picker (see DECISIONS.md) -
// the honest minimum that satisfies "reactions" without inventing a UI the brief didn't ask for.
@Entity
@Table(name = "arena_post_reactions", uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "user_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PostReaction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
