package com.vikisol.arena.posts.entity;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.entity.VerificationLevel;
import com.vikisol.arena.common.entity.BaseEntity;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// ARENA-V2-PRODUCT-ARCHITECTURE.md §6's generic post primitive, Phase A scope. Only ACTIVITY/ASK
// posts ever get PostJoinRequest rows or a Room; UPDATE/COMPANY posts are the simplest type
// (post it, it shows in the feed, comments/reactions apply generically to every post type).
@Entity
@Table(name = "arena_posts")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Post extends BaseEntity {

    // Always set - the acting account (candidate for ACTIVITY/ASK/UPDATE, the posting recruiter/
    // company_admin for COMPANY) so every post has one unambiguous owner for audit/permission
    // checks, even when authorCompany is also set and takes over for display purposes.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_user_id", nullable = false)
    private User authorUser;

    // Was a reserved raw UUID column with no JPA relation through Phase A/B ("company pages
    // don't exist yet"). Promoted to a real relation in the post-spec reconciliation pass -
    // §3.5/§6 explicitly want "Company posts appear in the feed," which needs this to actually
    // resolve to a company's name/emoji for display (see PostMapper). Column name unchanged
    // (author_company_id), so no migration was needed for the column itself - see
    // DECISIONS.md.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "author_company_id")
    private EnterpriseProfile authorCompany;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PostIntentType intentType;

    // ARENA-MASTER-ARCHITECTURE.md PART 7.5/7.6 - every post type now has an optional title
    // (composer's "title" field, PostCard's H2). Null is a valid, common case (ASK/UPDATE posts
    // are frequently title-less, body-only, same as before this field existed) - never
    // backfilled/required for existing rows.
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    // Freeform, author-typed description of roughly where (e.g. "Gachibowli") - always visible,
    // never precise enough alone to be a real address.
    private String locationText;

    // Phase B geo (only ever populated for ACTIVITY posts when the author had location consent
    // != OFF at creation time) - geohash/approxLat/approxLng are the SAME "always an
    // approximation, never the raw point" guarantee as CandidateProfile's own fields; the Map
    // screen applies a second, independent jitter on top of this at serve time. Null for
    // ASK/UPDATE posts and for ACTIVITY posts created with no location capture.
    private String geohash;
    private Double approxLat;
    private Double approxLng;

    // The real "where exactly" - a street address, a specific landmark, a video-call link,
    // whatever the author needs joiners to actually get there. Deliberately a SEPARATE field
    // from locationText/geohash: PostMapper only includes this in a PostResponse when the
    // viewer is the author or an approved room member (§4: "exact meeting point revealed only
    // inside the room, only to approved joiners"). Never touches the Map/Feed's public response.
    @Column(columnDefinition = "TEXT")
    private String exactMeetingPoint;

    // §4: "creators can require a verification level to join." Null = no requirement beyond
    // whatever PostVisibility already gates. Only meaningful for joinable (ACTIVITY/ASK) posts.
    @Enumerated(EnumType.STRING)
    private VerificationLevel requiredVerificationLevel;

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

    // PostLifecycleScheduler's reminder job sets this the moment it notifies room members that
    // an activity is starting soon, so a post is never reminded twice.
    private Instant remindedAt;

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

    // Phase C §7.3 feed ranking - comma-joined float vector, computed once at creation time (see
    // PostService.create) via the active EmbeddingProvider. Stored as TEXT, not a `vector`/array
    // column - see EmbeddingUtil's own comment for why.
    @Column(columnDefinition = "TEXT")
    private String embedding;

    public boolean isJoinable() {
        return intentType == PostIntentType.ACTIVITY || intentType == PostIntentType.ASK;
    }
}
