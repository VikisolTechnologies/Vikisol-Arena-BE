package com.vikisol.arena.agent.entity;

import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

// An action Jenny proposed in a reply (Phase 3) - shown to the user as a card with Approve /
// Not now, and only executed through JennySol once approved. See V17.
@Entity
@Table(name = "arena_agent_actions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AgentAction extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "message_id", nullable = false)
    private AgentMessage message;

    // JennySol's PendingAction id - the only handle that can execute it.
    @Column(nullable = false, length = 100, unique = true)
    private String externalActionId;

    @Column(nullable = false, length = 80)
    private String toolName;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String argsJson;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private AgentActionStatus status = AgentActionStatus.PENDING;

    @Column(columnDefinition = "TEXT")
    private String resultJson;

    @Column(length = 500)
    private String error;

    private java.time.Instant expiresAt;
}
