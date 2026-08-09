package com.vikisol.arena.follows.repository;

import com.vikisol.arena.follows.entity.UserBlock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserBlockRepository extends JpaRepository<UserBlock, UUID> {
    boolean existsByBlockerUserIdAndBlockedUserId(UUID blockerUserId, UUID blockedUserId);

    void deleteByBlockerUserIdAndBlockedUserId(UUID blockerUserId, UUID blockedUserId);

    @EntityGraph(attributePaths = "blockedUser")
    List<UserBlock> findByBlockerUserIdOrderByCreatedAtDesc(UUID blockerUserId);

    List<UserBlock> findByBlockerUserId(UUID blockerUserId);
}
