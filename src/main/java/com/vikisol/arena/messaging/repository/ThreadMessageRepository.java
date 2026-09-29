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

    // Row 37: the latest message of each conversation on a page, in one query.
    @org.springframework.data.jpa.repository.Query("""
            select m from ThreadMessage m where m.conversation.id in :ids
              and m.createdAt = (select max(m2.createdAt) from ThreadMessage m2 where m2.conversation = m.conversation)""")
    List<ThreadMessage> findLatestIn(@org.springframework.data.repository.query.Param("ids") java.util.Collection<UUID> ids);

    // DemoContentService.removeAll() - real FK gap found live (2026-09-15): ThreadMessage has
    // required FKs on both conversation_id and sender_user_id, and nothing ever cleared these
    // before deleting a demo Conversation/User, so removal 409ed the moment any demo account had
    // a real DM (seedDirectAndBidConversations() creates several every seed run).
    void deleteByConversationId(UUID conversationId);
}
