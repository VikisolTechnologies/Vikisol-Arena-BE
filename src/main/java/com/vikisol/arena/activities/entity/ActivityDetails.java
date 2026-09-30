package com.vikisol.arena.activities.entity;

import com.vikisol.arena.activities.entity.ActivityCatalogue.*;
import com.vikisol.arena.common.entity.BaseEntity;
import com.vikisol.arena.posts.entity.Post;
import jakarta.persistence.*;
import lombok.*;

// One row per ACTIVITY post: the flow doc's structured activity (ARENA-APP-FLOW §3 A1-A4,
// FE-API-GAPS row 23). Price and reach are mirrored onto the post (priceInr, linkOnly) for lists.
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
    @Column(length = 20)
    private Category category;

    @Column(length = 40)
    private String subtype;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private Level level;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    @Builder.Default
    private CostType costType = CostType.FREE;

    private Integer perPersonInr;

    @Column(length = 200)
    private String costNote;

    // The subtype's own questions (format, overs, distance…): {key: string | number | boolean | string[]}.
    @Column(name = "type_answers_json", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String typeAnswersJson = "{}";

    @Column(name = "bring_json", nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String bringJson = "[]";

    @Column(length = 300)
    private String accessibility;

    private Boolean indoor;

    private Integer minSize;

    @Enumerated(EnumType.STRING)
    @Column(name = "repeat_rule", nullable = false, length = 8)
    @Builder.Default
    private Repeat repeat = Repeat.ONCE;

    // A host label, not a person's attribute: setting it makes the activity approval-only.
    // There is no gender field anywhere.
    @Column(nullable = false)
    @Builder.Default
    private boolean womenOnly = false;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    @Builder.Default
    private Reach reach = Reach.NEARBY;

    private String coverUrl;

    @Column(nullable = false)
    @Builder.Default
    private boolean waitlistEnabled = true;
}
