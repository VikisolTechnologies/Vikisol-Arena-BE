package com.vikisol.arena.enterprise.entity;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.entity.BaseEntity;
import com.vikisol.arena.profile.entity.Industry;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "arena_enterprise_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class EnterpriseProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private String companyName;

    @Column(nullable = false)
    private String logoEmoji;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Industry industry;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private CompanySize size;

    @ElementCollection
    @CollectionTable(name = "arena_enterprise_hiring_for", joinColumns = @JoinColumn(name = "enterprise_id"))
    @Column(name = "role_name")
    @Builder.Default
    private List<String> hiringFor = new ArrayList<>();

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private Plan plan = Plan.FREE;

    @Column(nullable = false)
    @Builder.Default
    private int seatsUsed = 1;

    @Column(nullable = false)
    @Builder.Default
    private int seatsTotal = 1;

    @Column(nullable = false)
    @Builder.Default
    private int unlockCreditsUsed = 0;

    @Column(nullable = false)
    @Builder.Default
    private int unlockCreditsTotal = 10;

    // Platform-admin-controlled (PA1: suspend/reactivate tenant) - a suspended tenant's users
    // can't sign in (see AuthService), independent of any individual user's own account state.
    // columnDefinition default needed so ddl-auto:update's ALTER TABLE ADD COLUMN NOT NULL
    // succeeds against this table's pre-existing rows (same pattern as Milestone.amount).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(255) not null default 'ACTIVE'")
    @Builder.Default
    private TenantStatus status = TenantStatus.ACTIVE;
}
