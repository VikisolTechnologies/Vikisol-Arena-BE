package com.vikisol.arena.follows.entity;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

// Independent join entity, same shape as marketplace's Bid - NOT enterprise.Membership's shape
// (that enforces a 1:1 unique-per-user relationship, wrong for a many-to-many follow graph).
// No company-follow column - that target doesn't exist until Phase C's company pages land.
@Entity
@Table(name = "arena_follows", uniqueConstraints = @UniqueConstraint(columnNames = {"follower_user_id", "following_user_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Follow extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_user_id", nullable = false)
    private User followerUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "following_user_id", nullable = false)
    private User followingUser;
}
