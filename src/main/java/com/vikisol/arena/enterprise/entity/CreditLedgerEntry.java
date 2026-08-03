package com.vikisol.arena.enterprise.entity;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "arena_credit_ledger")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CreditLedgerEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false)
    private EnterpriseProfile tenant;

    // Who spent/granted it - null for platform-admin-issued grants that aren't tied to a
    // specific recruiter action.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actor;

    // Negative for spends (unlock), positive for grants (plan credit refresh, support grant).
    @Column(nullable = false)
    private int delta;

    @Column(nullable = false)
    private String reason;

    @Column(nullable = false)
    private int balanceAfter;
}
