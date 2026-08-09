package com.vikisol.arena.follows.repository;

import com.vikisol.arena.follows.entity.Follow;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface FollowRepository extends JpaRepository<Follow, UUID> {
    Optional<Follow> findByFollowerUserIdAndFollowingUserId(UUID followerUserId, UUID followingUserId);

    boolean existsByFollowerUserIdAndFollowingUserId(UUID followerUserId, UUID followingUserId);

    long countByFollowingUserId(UUID followingUserId);

    long countByFollowerUserId(UUID followerUserId);

    // Used by FeedRankingService's follow-affinity term - just the ids, no need to hydrate User.
    @Query("select f.followingUser.id from Follow f where f.followerUser.id = :followerUserId")
    List<UUID> findFollowingUserIdsByFollowerUserId(@Param("followerUserId") UUID followerUserId);

    @EntityGraph(attributePaths = "followerUser")
    List<Follow> findByFollowingUserIdOrderByCreatedAtDesc(UUID followingUserId);

    @EntityGraph(attributePaths = "followingUser")
    List<Follow> findByFollowerUserIdOrderByCreatedAtDesc(UUID followerUserId);
}
