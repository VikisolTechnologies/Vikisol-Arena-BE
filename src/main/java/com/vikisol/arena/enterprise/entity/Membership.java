package com.vikisol.arena.enterprise.entity;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// The real many-users-per-tenant link (see DECISIONS.md). EnterpriseProfile.user stays as
// "the founding admin" for backward compat, but every enterprise-ish request resolves its
// tenant via this table, not that field - uniform for recruiter/company_admin/hiring_manager
// alike, and for however many people end up on one company's account.
@Entity
@Table(name = "arena_memberships")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Membership extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private EnterpriseProfile tenant;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private MembershipStatus status = MembershipStatus.ACTIVE;

    // Nullable - the tenant's founding admin isn't "invited" by anyone.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invited_by_user_id")
    private User invitedBy;

    private Instant joinedAt;
}
