package com.vikisol.arena.marketplace.repository;

import com.vikisol.arena.marketplace.entity.Project;
import com.vikisol.arena.marketplace.entity.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    Page<Project> findByStatus(ProjectStatus status, Pageable pageable);
    Page<Project> findByPostedByUserId(UUID userId, Pageable pageable);
}
