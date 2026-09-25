package com.vikisol.arena.posts.entity;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

// ARENA-V2-PRODUCT-ARCHITECTURE.md Phase C. Field-for-field continuation of the
// ThreadMessage -> RoomMessage lineage (post FK, author FK, content TEXT), generalized again
// from "N-ary room membership" to "any post, any authenticated user, no membership gate" - see
// DECISIONS.md. Enabled on every post type (not just UPDATE) - ACTIVITY/ASK posts get both a
// Room (for approved joiners to coordinate) and comments (for anyone to discuss publicly),
// which are two different things, not a replacement for each other.
@Entity
@Table(name = "arena_post_comments")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PostComment extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_user_id", nullable = false)
    private User authorUser;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String content;

    // Phase 2 (Discuss) threaded replies - the comment this one answers; null = top-level.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_comment_id")
    private PostComment parentComment;

    // Soft delete for a comment that still has replies - see V14 and PostCommentService.
    @Column(nullable = false)
    @Builder.Default
    private boolean deleted = false;
}
