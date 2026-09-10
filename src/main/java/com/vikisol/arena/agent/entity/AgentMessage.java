package com.vikisol.arena.agent.entity;

import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "arena_agent_messages")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AgentMessage extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "conversation_id", nullable = false)
    private AgentConversation conversation;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private AgentMessageRole role;

    @Column(nullable = false, columnDefinition = "text")
    private String content;

    // True on the honest "the agent isn't reachable right now" message a Noop/unreachable
    // AgentServiceClient produces - lets the frontend render a distinct Retry affordance instead
    // of treating this like a real assistant reply. See ARENA-DOCUMENT-3 §3/§14: never fake a
    // response when the real service is unavailable.
    @Column(nullable = false)
    @Builder.Default
    private boolean serviceUnavailable = false;
}
