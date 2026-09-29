package com.vikisol.arena.business.entity;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.entity.BaseEntity;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// A company's proof that it controls its website domain (G27): a code sent to a work email at
// that domain. VERIFIED only after the code is confirmed; changing the details starts over.
@Entity
@Table(name = "arena_business_verifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class BusinessVerification extends BaseEntity {

    public enum Status { PENDING, VERIFIED }

    // "Your role" on the company workspace screen: the submitter's job at the company. It is
    // not a permission; permissions are the account role (see TeamRoles).
    public enum SubmitterRole { FOUNDER, HR, TALENT_ACQUISITION, HIRING_MANAGER, OPERATIONS, OTHER }

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "tenant_id", nullable = false, unique = true)
    private EnterpriseProfile tenant;

    @Column(nullable = false, length = 200)
    private String legalName;

    @Column(nullable = false)
    private String website;

    @Column(nullable = false)
    private String domain;

    @Column(nullable = false)
    private String workEmail;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SubmitterRole submitterRole;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private Status status = Status.PENDING;

    @Column(length = 100)
    private String codeHash;

    private Instant codeExpiresAt;

    @Column(nullable = false)
    @Builder.Default
    private int attempts = 0;

    private Instant lastSentAt;

    private Instant verifiedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "verified_by_user_id")
    private User verifiedBy;
}
