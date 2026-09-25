package com.vikisol.arena.communities.entity;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

// Arena restructure Phase 2 (Discuss) - a user-created space for discussions around one topic
// or place. Whoever creates it is its OWNER (see CommunityMember). See V15.
@Entity
@Table(name = "arena_communities")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Community extends BaseEntity {

    @Column(nullable = false, length = 40, unique = true)
    private String slug;

    @Column(nullable = false, length = 60)
    private String name;

    @Column(length = 500)
    private String description;

    @Column(nullable = false, length = 16)
    private String emoji;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_user_id", nullable = false)
    private User createdBy;

    @Column(nullable = false)
    @Builder.Default
    private boolean allowAnonymous = true;
}
