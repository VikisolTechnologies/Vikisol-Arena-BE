package com.vikisol.arena.communities.repository;

import com.vikisol.arena.communities.entity.CommunityMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface CommunityMemberRepository extends JpaRepository<CommunityMember, UUID> {
    // DemoContentService.removeAll - bulk, so they run as single statements.
    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @Query("delete from CommunityMember m where m.community.id = :communityId")
    void deleteByCommunityId(@Param("communityId") UUID communityId);

    @org.springframework.data.jpa.repository.Modifying(flushAutomatically = true)
    @Query("delete from CommunityMember m where m.user.id = :userId")
    void deleteByUserId(@Param("userId") UUID userId);

    Optional<CommunityMember> findByCommunityIdAndUserId(UUID communityId, UUID userId);

    @EntityGraph(attributePaths = "community")
    List<CommunityMember> findByUserIdAndBannedFalse(UUID userId);

    @EntityGraph(attributePaths = "user")
    List<CommunityMember> findByCommunityIdAndRoleIn(UUID communityId, Collection<com.vikisol.arena.communities.entity.CommunityRole> roles);

    interface CommunityCount {
        UUID getCommunityId();

        long getCnt();
    }

    @Query("select m.community.id as communityId, count(m) as cnt from CommunityMember m where m.banned = false and m.community.id in :ids group by m.community.id")
    List<CommunityCount> countMembers(@Param("ids") Collection<UUID> ids);

    @Query("select m from CommunityMember m where m.user.id = :userId and m.community.id in :ids")
    List<CommunityMember> findMine(@Param("userId") UUID userId, @Param("ids") Collection<UUID> ids);
}
