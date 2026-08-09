package com.vikisol.arena.posts.entity;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

// ARENA-V2-PRODUCT-ARCHITECTURE.md §6's generic post primitive, Phase A scope. authorCompanyId
// is a reserved raw column (no JPA relation) for Phase C company pages - never populated this
// phase, same "schema now, UI never yet" treatment as locationText getting no map/geo picker
// until Phase B. Only ACTIVITY/ASK posts ever get PostJoinRequest rows or a Room; UPDATE posts
// are deliberately the simplest type (post it, it shows in the feed, nothing else) since
// comments/reactions are explicitly Phase C in the source doc's own phase list.
@Entity
@Table(name = "arena_posts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Post extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_user_id", nullable = false)
    private User authorUser;

    // Reserved for Phase C company pages - no FK, never populated in Phase A.
    @Column(name = "author_company_id")
    private UUID authorCompanyId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PostIntentType intentType;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    // Freeform text only - no geo/H3/PostGIS this phase (Phase B). Never rendered as a map pin.
    private String locationText;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PostAudience audience = PostAudience.GLOBAL;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PostVisibility visibility = PostVisibility.PUBLIC;

    private Integer capacity;

    @Column(nullable = false)
    @Builder.Default
    private int spotsFilled = 0;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PostStatus status = PostStatus.OPEN;

    private Instant startsAt;
    private Instant endsAt;

    @ElementCollection
    @CollectionTable(name = "arena_post_tags", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "tag")
    @Builder.Default
    private List<String> tags = new ArrayList<>();

    @ElementCollection
    @CollectionTable(name = "arena_post_media", joinColumns = @JoinColumn(name = "post_id"))
    @Column(name = "url")
    @Builder.Default
    private List<String> mediaUrls = new ArrayList<>();

    public boolean isJoinable() {
        return intentType == PostIntentType.ACTIVITY || intentType == PostIntentType.ASK;
    }
}
