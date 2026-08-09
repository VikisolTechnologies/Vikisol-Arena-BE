package com.vikisol.arena.posts.repository;

import com.vikisol.arena.posts.entity.PostJoinRequest;
import com.vikisol.arena.posts.entity.PostJoinStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostJoinRequestRepository extends JpaRepository<PostJoinRequest, UUID> {
    @EntityGraph(attributePaths = "user")
    List<PostJoinRequest> findByPostIdOrderByCreatedAtAsc(UUID postId);

    Optional<PostJoinRequest> findByPostIdAndUserId(UUID postId, UUID userId);

    long countByPostIdAndStatus(UUID postId, PostJoinStatus status);
}
