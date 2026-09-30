package com.vikisol.arena.marketplace.repository;

import com.vikisol.arena.marketplace.entity.Project;
import com.vikisol.arena.marketplace.entity.ProjectStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ProjectRepository extends JpaRepository<Project, UUID> {
    @EntityGraph(attributePaths = "postedByUser")
    Page<Project> findByStatus(ProjectStatus status, Pageable pageable);

    // Batched warm-up of `skills` for a list of projects (PERFORMANCE.md).
    @EntityGraph(attributePaths = "skills")
    @org.springframework.data.jpa.repository.Query("select p from Project p where p.id in :ids")
    java.util.List<Project> findByIdInFetchingSkills(@org.springframework.data.repository.query.Param("ids") java.util.Collection<UUID> ids);

    @EntityGraph(attributePaths = "postedByUser")
    Page<Project> findByPostedByUserId(UUID userId, Pageable pageable);

    long countByStatus(ProjectStatus status);

    long countByStatusAndDemoContentFalse(ProjectStatus status);

    @EntityGraph(attributePaths = "postedByUser")
    Page<Project> findByStatusAndDemoContentFalse(ProjectStatus status, Pageable pageable);

    // DemoContentService - see PostRepository.findByDemoContentTrue()'s own comment.
    List<Project> findByDemoContentTrue();
}
