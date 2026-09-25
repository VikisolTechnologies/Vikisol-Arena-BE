package com.vikisol.arena.agent.repository;

import com.vikisol.arena.agent.entity.AgentAction;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public interface AgentActionRepository extends JpaRepository<AgentAction, UUID> {
    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select a from AgentAction a where a.id = :id and a.message.conversation.user.id = :userId")
    java.util.Optional<AgentAction> findOwnedForUpdate(@org.springframework.data.repository.query.Param("id") UUID id,
            @org.springframework.data.repository.query.Param("userId") UUID userId);

    List<AgentAction> findByMessageIdInOrderByCreatedAtAsc(Collection<UUID> messageIds);
}
