package com.vikisol.arena.posts.repository;

import com.vikisol.arena.posts.entity.PostComment;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.UUID;

public interface PostCommentRepository extends JpaRepository<PostComment, UUID> {
    @EntityGraph(attributePaths = "authorUser")
    List<PostComment> findByPostIdOrderByCreatedAtAsc(UUID postId);

    long countByPostId(UUID postId);

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
