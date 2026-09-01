package com.vikisol.arena.messaging.repository;

import com.vikisol.arena.messaging.entity.ThreadMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface ThreadMessageRepository extends JpaRepository<ThreadMessage, UUID> {
    // P3 audit fix: ConversationService.getMessages had no limit at all - a long-running DM
    // thread returned every message ever sent in one response/query. Capped at the 100 most
    // recent (service reverses to ascending for display), same pragmatic cap as
    // RoomMessageRepository.findTop100ByRoomIdOrderByCreatedAtDesc rather than a full pagination
    // rework of this endpoint's contract.
    List<ThreadMessage> findTop100ByConversationIdOrderByCreatedAtDesc(UUID conversationId);
}
