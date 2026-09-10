package com.vikisol.arena.agent.repository;

import com.vikisol.arena.agent.entity.AgentConversation;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface AgentConversationRepository extends JpaRepository<AgentConversation, UUID> {
    Optional<AgentConversation> findFirstByUserIdOrderByUpdatedAtDesc(UUID userId);
}
