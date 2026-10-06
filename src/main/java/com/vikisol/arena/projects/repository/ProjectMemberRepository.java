package com.vikisol.arena.projects.repository;

import com.vikisol.arena.projects.entity.ProjectMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectMemberRepository extends JpaRepository<ProjectMember, UUID> {

    Optional<ProjectMember> findByPostIdAndUserId(UUID postId, UUID userId);

    @EntityGraph(attributePaths = {"user", "role"})
    List<ProjectMember> findByPostId(UUID postId);

    boolean existsByRolePostId(UUID postId);

    // People who are in (approved join) for a role.
    @Query("""
            select count(m) from ProjectMember m, com.vikisol.arena.posts.entity.PostJoinRequest j
            where m.role.id = :roleId and j.post.id = m.post.id and j.user.id = m.user.id
              and j.status = com.vikisol.arena.posts.entity.PostJoinStatus.APPROVED
            """)
    long countApprovedInRole(@Param("roleId") UUID roleId);
}
