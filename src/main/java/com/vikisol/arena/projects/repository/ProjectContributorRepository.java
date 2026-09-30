package com.vikisol.arena.projects.repository;

import com.vikisol.arena.projects.entity.ProjectContributor;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProjectContributorRepository extends JpaRepository<ProjectContributor, UUID> {

    List<ProjectContributor> findByPostId(UUID postId);

    List<ProjectContributor> findByUserId(UUID userId);

    boolean existsByPostIdAndUserId(UUID postId, UUID userId);
}
