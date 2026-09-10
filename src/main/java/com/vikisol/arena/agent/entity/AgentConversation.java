package com.vikisol.arena.agent.entity;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

// Replaces the browser-only ChatMessage[] state the /agent page used to keep in React state
// (lost on refresh/device change) - see agent/page.tsx's former buildReply() keyword matcher,
// removed because it was never backed by a real AI service. Conversation history now survives
// refresh/device change the same way every other Arena feature does: server-side, not localStorage.
@Entity
@Table(name = "arena_agent_conversations")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AgentConversation extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    private String title;
}
