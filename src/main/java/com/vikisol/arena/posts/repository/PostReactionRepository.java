package com.vikisol.arena.posts.repository;

import com.vikisol.arena.posts.entity.PostReaction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface PostReactionRepository extends JpaRepository<PostReaction, UUID> {
    Optional<PostReaction> findByPostIdAndUserId(UUID postId, UUID userId);

    boolean existsByPostIdAndUserId(UUID postId, UUID userId);

    long countByPostId(UUID postId);

    // PostService.delete() - a hard delete needs its dependents gone first (FK on post_id).
    void deleteByPostId(UUID postId);

    @Query("select r.post.id as postId, count(r) as cnt from PostReaction r where r.post.id in :postIds group by r.post.id")
    List<PostCommentRepository.PostCountProjection> countByPostIdIn(@Param("postIds") List<UUID> postIds);

    // Batched "did I react" check for a feed/trending window - mirrors batchUnlockedCandidateIds'
    // "one Set for the whole page" shape.
    @Query("select r.post.id from PostReaction r where r.user.id = :userId and r.post.id in :postIds")
    Set<UUID> findReactedPostIdsByUserIdAndPostIdIn(@Param("userId") UUID userId, @Param("postIds") List<UUID> postIds);
}
