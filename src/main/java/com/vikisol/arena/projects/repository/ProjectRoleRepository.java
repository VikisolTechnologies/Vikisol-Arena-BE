package com.vikisol.arena.projects.repository;

import com.vikisol.arena.projects.entity.ProjectRole;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProjectRoleRepository extends JpaRepository<ProjectRole, UUID> {
    List<ProjectRole> findByPostIdOrderByPositionAsc(UUID postId);

    void deleteByPostId(UUID postId);
}
