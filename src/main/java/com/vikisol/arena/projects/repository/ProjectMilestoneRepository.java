package com.vikisol.arena.projects.repository;

import com.vikisol.arena.projects.entity.ProjectMilestone;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProjectMilestoneRepository extends JpaRepository<ProjectMilestone, UUID> {

    List<ProjectMilestone> findByPostIdOrderByPositionAscIdAsc(UUID postId);

    List<ProjectMilestone> findByCreatedById(UUID userId);
}
