package com.vikisol.arena.posts.repository;

import com.vikisol.arena.posts.entity.PostSave;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

public interface PostSaveRepository extends JpaRepository<PostSave, UUID> {
    Optional<PostSave> findByPostIdAndUserId(UUID postId, UUID userId);

    // PostService.delete() - a hard delete needs its dependents gone first (FK on post_id).
    void deleteByPostId(UUID postId);

    boolean existsByPostIdAndUserId(UUID postId, UUID userId);

    @EntityGraph(attributePaths = "post")
    Page<PostSave> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    @Query("select s.post.id from PostSave s where s.user.id = :userId and s.post.id in :postIds")
    Set<UUID> findSavedPostIdsByUserIdAndPostIdIn(@Param("userId") UUID userId, @Param("postIds") List<UUID> postIds);
}
