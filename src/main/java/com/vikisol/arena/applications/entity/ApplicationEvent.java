package com.vikisol.arena.applications.entity;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

// One step in an application's history (FE-API-GAPS row 30): applied, a stage change (with the
// company's message to the candidate, row 31), withdrawn, an offer accepted or declined.
@Entity
@Table(name = "arena_application_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ApplicationEvent extends BaseEntity {

    public enum Type { APPLIED, STAGE, WITHDRAWN, OFFER_ACCEPTED, OFFER_DECLINED }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 24)
    private Type type;

    @Enumerated(EnumType.STRING)
    @Column(length = 24)
    private ApplicationStage stage;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actor;

    @Column(length = 600)
    private String message;
}
