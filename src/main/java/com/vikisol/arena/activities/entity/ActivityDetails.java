package com.vikisol.arena.activities.entity;

import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import com.vikisol.arena.posts.entity.Post;

// One row per ACTIVITY post with the typed extras the post itself doesn't carry (G7, G13).
@Entity
@Table(name = "arena_activity_details")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ActivityDetails extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false, unique = true)
    private Post post;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private ActivityKind kind;

    // Map<String, String> as JSON; keys limited by ActivityKind.
    @Column(name = "details_json", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String detailsJson = "{}";

    private String coverUrl;

    @Column(nullable = false)
    @Builder.Default
    private boolean waitlistEnabled = true;
}
