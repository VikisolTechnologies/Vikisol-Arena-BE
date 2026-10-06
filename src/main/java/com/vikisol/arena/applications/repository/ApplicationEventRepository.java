package com.vikisol.arena.applications.repository;

import com.vikisol.arena.applications.entity.ApplicationEvent;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ApplicationEventRepository extends JpaRepository<ApplicationEvent, UUID> {
    @EntityGraph(attributePaths = "actor")
    List<ApplicationEvent> findByApplicationIdOrderByCreatedAtAscIdAsc(UUID applicationId);
}
