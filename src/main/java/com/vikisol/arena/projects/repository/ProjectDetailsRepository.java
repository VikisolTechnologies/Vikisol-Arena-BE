package com.vikisol.arena.projects.repository;

import com.vikisol.arena.projects.entity.ProjectDetails;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface ProjectDetailsRepository extends JpaRepository<ProjectDetails, UUID> {

    Optional<ProjectDetails> findByPostId(UUID postId);

    List<ProjectDetails> findByPostIdIn(Collection<UUID> postIds);
}
