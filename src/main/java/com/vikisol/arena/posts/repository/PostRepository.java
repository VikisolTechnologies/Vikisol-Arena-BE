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
    @EntityGraph(attributePaths = "authorUser")
    Page<Post> findByStatusOrderByCreatedAtDesc(PostStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "authorUser")
    Page<Post> findByAuthorUserIdOrderByCreatedAtDesc(UUID authorUserId, Pageable pageable);

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
}
