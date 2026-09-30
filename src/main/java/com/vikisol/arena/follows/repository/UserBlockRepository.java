package com.vikisol.arena.follows.repository;

import com.vikisol.arena.follows.entity.UserBlock;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface UserBlockRepository extends JpaRepository<UserBlock, UUID> {
    boolean existsByBlockerUserIdAndBlockedUserId(UUID blockerUserId, UUID blockedUserId);

    void deleteByBlockerUserIdAndBlockedUserId(UUID blockerUserId, UUID blockedUserId);

    @EntityGraph(attributePaths = "blockedUser")
    Page<UserBlock> findByBlockerUserIdOrderByCreatedAtDescIdDesc(UUID blockerUserId, Pageable pageable);

    List<UserBlock> findByBlockerUserId(UUID blockerUserId);

    // PERFORMANCE.md: everyone on the other side of a block with this user, either direction, in
    // one query (filtering a feed window used to cost two queries per post).
    @org.springframework.data.jpa.repository.Query("""
            select case when b.blockerUser.id = :userId then b.blockedUser.id else b.blockerUser.id end
            from UserBlock b where b.blockerUser.id = :userId or b.blockedUser.id = :userId""")
    List<UUID> findBlockedEitherDirection(@org.springframework.data.repository.query.Param("userId") UUID userId);
}
