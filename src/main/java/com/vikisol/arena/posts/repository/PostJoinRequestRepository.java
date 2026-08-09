package com.vikisol.arena.posts.repository;

import com.vikisol.arena.posts.entity.PostJoinRequest;
import com.vikisol.arena.posts.entity.PostJoinStatus;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface PostJoinRequestRepository extends JpaRepository<PostJoinRequest, UUID> {
    @EntityGraph(attributePaths = "user")
    List<PostJoinRequest> findByPostIdOrderByCreatedAtAsc(UUID postId);

    Optional<PostJoinRequest> findByPostIdAndUserId(UUID postId, UUID userId);

    long countByPostIdAndStatus(UUID postId, PostJoinStatus status);

    // §4 safety-audit fix: "Show join-count ... and account age" (a trust signal for whoever's
    // about to meet a stranger from an ACTIVITY/ASK post) - how many other posts this person has
    // actually been approved into elsewhere, a track record of real participation. Batched per
    // feed/nearby window, same shape as every other batch-count query in PostMapper.
    @Query("select j.user.id as userId, count(j) as cnt from PostJoinRequest j " +
            "where j.status = com.vikisol.arena.posts.entity.PostJoinStatus.APPROVED and j.user.id in :userIds group by j.user.id")
    List<UserJoinCountProjection> countApprovedByUserIdIn(@Param("userIds") List<UUID> userIds);

    interface UserJoinCountProjection {
        UUID getUserId();
        long getCnt();
    }
}
