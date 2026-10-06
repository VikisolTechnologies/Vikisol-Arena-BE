package com.vikisol.arena.agent.repository;

import com.vikisol.arena.agent.entity.AgentMessage;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface AgentMessageRepository extends JpaRepository<AgentMessage, UUID> {
    // Newest first so page 0 is always the latest messages; id breaks same-instant ties.
    Page<AgentMessage> findByConversationIdOrderByCreatedAtDescIdDesc(UUID conversationId, Pageable pageable);

    // Bounds how much transcript a future real AgentServiceClient is handed per turn - see
    // ARENA-CONTINUATION-REBUILD §105 (AI cost control: bounded context, not the full history
    // on every request).
    List<AgentMessage> findTop20ByConversationIdOrderByCreatedAtDesc(UUID conversationId);
}
