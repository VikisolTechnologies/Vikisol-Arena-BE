package com.vikisol.arena.audit.entity;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.entity.BaseEntity;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import jakarta.persistence.*;
import lombok.*;

// Written explicitly at each meaningful action site (see AuditService, DECISIONS.md) rather
// than via an aspect - `action` is a free-text namespaced string (see AuditActions constants)
// so new action types never require a migration. `createdAt` (from BaseEntity) is the
// timestamp CA3/PA1 filter and sort on.
@Entity
@Table(name = "arena_audit_events")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class AuditEvent extends BaseEntity {

    // Nullable: a handful of platform_admin actions aren't scoped to one tenant (e.g. global
    // moderation queue review before a takedown target is known).
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id")
    private EnterpriseProfile tenant;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id", nullable = false)
    private User actor;

    @Column(nullable = false)
    private String action;

    // Short human-readable label for what was acted on, e.g. "Backend Engineer posting" or
    // "candidate Priya Nair".
    private String target;

    @Column(columnDefinition = "TEXT")
    private String metadata;
}
