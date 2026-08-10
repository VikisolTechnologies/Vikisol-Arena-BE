package com.vikisol.arena.posts.entity;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

// ARENA-MASTER-ARCHITECTURE.md PART 6 "SAVE POST|DELETE /posts/{id}/save GET /me/saved" -
// independent join entity, same shape as PostReaction/Follow (not a column on Post, since it's
// per-viewer, many-to-many).
@Entity
@Table(name = "arena_post_saves", uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "user_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PostSave extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;
}
