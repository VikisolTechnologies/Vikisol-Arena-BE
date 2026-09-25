package com.vikisol.arena.communities.entity;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

// One row per (community, user). Same independent-join-entity shape as Follow/PostReaction.
@Entity
@Table(name = "arena_community_members", uniqueConstraints = @UniqueConstraint(columnNames = {"community_id", "user_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CommunityMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "community_id", nullable = false)
    private Community community;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private CommunityRole role;

    @Column(nullable = false)
    @Builder.Default
    private boolean banned = false;
}
