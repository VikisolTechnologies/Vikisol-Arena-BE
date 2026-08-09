package com.vikisol.arena.posts.repository;

import com.vikisol.arena.posts.entity.Post;
import com.vikisol.arena.posts.entity.PostStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface PostRepository extends JpaRepository<Post, UUID> {
    // Bounded recent-post window for FeedRankingService to score in Java - see its own comment
    // for why there's no single ORDER BY that expresses a follows+recency composite score.
    @EntityGraph(attributePaths = "authorUser")
    Page<Post> findByStatusOrderByCreatedAtDesc(PostStatus status, Pageable pageable);

    @EntityGraph(attributePaths = "authorUser")
    Page<Post> findByAuthorUserIdOrderByCreatedAtDesc(UUID authorUserId, Pageable pageable);
}
