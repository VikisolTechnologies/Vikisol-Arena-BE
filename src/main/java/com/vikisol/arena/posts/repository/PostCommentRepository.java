package com.vikisol.arena.posts.repository;

import com.vikisol.arena.posts.entity.PostComment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PostCommentRepository extends JpaRepository<PostComment, UUID> {
    // P3 audit fix: PostCommentService.getComments had no limit at all - a viral post's comment
    // section would return every comment ever posted, and load one CandidateProfile per comment
    // on top of that (see batchAuthorProfiles). Capped at the 200 most recent (service reverses
    // to ascending for display) - the same pragmatic "recent window instead of unbounded" trade
    // as RoomMessageRepository/ThreadMessageRepository, sized up a little since a comment thread
    // is read in full more often than a chat's full history is.
    @EntityGraph(attributePaths = "authorUser")
    List<PostComment> findTop200ByPostIdOrderByCreatedAtDesc(UUID postId);

    long countByPostId(UUID postId);

    // PostService.delete() - a hard delete needs its dependents gone first (FK on post_id).
    // One bulk statement, not Spring's derived delete-by (which removes rows one at a time in
    // load order): with threaded replies (V14) a parent could go before its reply and trip the
    // self-referencing FK, whereas Postgres checks a single statement's FKs once, at its end.
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true, clearAutomatically = true)
    @org.springframework.data.jpa.repository.Query("delete from PostComment c where c.post.id = :postId")
    void deleteByPostId(@org.springframework.data.repository.query.Param("postId") UUID postId);

    boolean existsByParentCommentId(UUID parentCommentId);

    // Phase 2 part C rate limit - anonymous replies by one author since a moment.
    long countByAuthorUserIdAndAnonymousTrueAndCreatedAtAfter(UUID authorUserId, java.time.Instant since);

    // Batched comment-count warm-up for a feed/trending window - one query for the whole page
    // instead of one per post, same shape as every other batch-count method in this codebase
    // (TalentSearchService.batchUnlockedCandidateIds, etc).
    @Query("select c.post.id as postId, count(c) as cnt from PostComment c where c.post.id in :postIds group by c.post.id")
    List<PostCountProjection> countByPostIdIn(@Param("postIds") List<UUID> postIds);

    interface PostCountProjection {
        UUID getPostId();
        long getCnt();
    }
}
