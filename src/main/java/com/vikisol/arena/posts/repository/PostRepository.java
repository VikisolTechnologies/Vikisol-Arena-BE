package com.vikisol.arena.posts.repository;

import com.vikisol.arena.posts.entity.Post;
import com.vikisol.arena.posts.entity.PostIntentType;
import com.vikisol.arena.posts.entity.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {
    // Bounded recent-post window for FeedRankingService to score in Java - see its own comment
    // for why there's no single ORDER BY that expresses a follows+recency composite score.
    // authorCompany included since COMPANY posts now appear in this same feed window.
    @EntityGraph(attributePaths = {"authorUser", "authorCompany"})
    Page<Post> findByStatusOrderByCreatedAtDesc(PostStatus status, Pageable pageable);

    // Search candidates - live posts only (open or full), newest first.
    Page<Post> findByStatusInOrderByCreatedAtDesc(java.util.Collection<PostStatus> statuses, Pageable pageable);

    @EntityGraph(attributePaths = "authorUser")
    Page<Post> findByAuthorUserIdOrderByCreatedAtDesc(UUID authorUserId, Pageable pageable);

    // Company page's own post history (post-spec reconciliation - §3.5/§6 company posting).
    @EntityGraph(attributePaths = {"authorUser", "authorCompany"})
    Page<Post> findByAuthorCompanyIdOrderByCreatedAtDesc(UUID authorCompanyId, Pageable pageable);

    // PostLifecycleScheduler's reminder job: OPEN activities starting within the window that
    // haven't been reminded yet.
    @EntityGraph(attributePaths = "authorUser")
    @Query("select p from Post p where p.status = :status and p.intentType = :intentType " +
            "and p.remindedAt is null and p.startsAt is not null and p.startsAt between :now and :horizon")
    List<Post> findDueForReminder(@Param("status") PostStatus status, @Param("intentType") PostIntentType intentType,
                                   @Param("now") Instant now, @Param("horizon") Instant horizon);

    // PostLifecycleScheduler's expiry job: OPEN/FULL posts genuinely past their own explicit
    // endsAt, OR (no explicit endsAt at all) well past their startsAt - a post that never said
    // when it ends but started 2+ hours ago is stale, not still "open."
    @Query("select p from Post p where p.status in :statuses and " +
            "((p.endsAt is not null and p.endsAt < :now) or (p.endsAt is null and p.startsAt is not null and p.startsAt < :staleHorizon))")
    List<Post> findExpirable(@Param("statuses") List<PostStatus> statuses, @Param("now") Instant now, @Param("staleHorizon") Instant staleHorizon);

    // P3 audit fix: PostMapper.toResponse read post.getTags()/getMediaUrls() directly, one lazy
    // load each per post per page (2 extra queries per row) - right next to the exact code
    // (PostService.toResponseList) that already correctly batches comment/reaction counts and
    // author-join-counts, tags/media just never got the same treatment. Two flat (postId, value)
    // projections instead, grouped into a Map<UUID, List<String>> once per page (see
    // PostService.batchTags/batchMediaUrls) - same "IN-query instead of N lazy loads" shape as
    // CandidateProfileRepository.findByIdInFetchingSkills.
    @Query("select p.id as postId, t as value from Post p join p.tags t where p.id in :postIds")
    List<PostElementProjection> findTagsByPostIdIn(@Param("postIds") List<UUID> postIds);

    @Query("select p.id as postId, m as value from Post p join p.mediaUrls m where p.id in :postIds")
    List<PostElementProjection> findMediaUrlsByPostIdIn(@Param("postIds") List<UUID> postIds);

    interface PostElementProjection {
        UUID getPostId();
        String getValue();
    }

    // DemoContentService - the on-demand, labeled/removable content overlay (ARENA-WEB-AND-SEED.md
    // Part 4), distinct from the original DataSeeder bootstrap (which never set this flag).
    @EntityGraph(attributePaths = "authorUser")
    List<Post> findByDemoContentTrue();
}
