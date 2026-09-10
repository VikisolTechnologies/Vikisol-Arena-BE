package com.vikisol.arena.agent.repository;

import com.vikisol.arena.agent.entity.AgentMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface AgentMessageRepository extends JpaRepository<AgentMessage, UUID> {
    List<AgentMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);

    // Bounds how much transcript a future real AgentServiceClient is handed per turn - see
    // ARENA-CONTINUATION-REBUILD §105 (AI cost control: bounded context, not the full history
    // on every request).
    List<AgentMessage> findTop20ByConversationIdOrderByCreatedAtDesc(UUID conversationId);
}
