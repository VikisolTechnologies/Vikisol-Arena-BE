package com.vikisol.arena.messaging.repository;

import com.vikisol.arena.messaging.entity.ThreadMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ThreadMessageRepository extends JpaRepository<ThreadMessage, UUID> {
    List<ThreadMessage> findByConversationIdOrderByCreatedAtAsc(UUID conversationId);
}
