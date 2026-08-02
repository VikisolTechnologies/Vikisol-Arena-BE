package com.vikisol.arena.enterprise.entity;

import com.vikisol.arena.common.entity.BaseEntity;
import com.vikisol.arena.profile.entity.CandidateProfile;
import jakarta.persistence.*;
import lombok.*;

// Records who unlocked which candidate profile and when - gives the candidate side an audit
// trail of enterprise-unlock events, something AUDIT.md flagged as missing entirely in the mock
// ("candidates have no visibility into who unlocked their profile or when").
@Entity
@Table(name = "arena_unlocked_candidates", uniqueConstraints = @UniqueConstraint(columnNames = {"enterprise_id", "candidate_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class UnlockedCandidate extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enterprise_id", nullable = false)
    private EnterpriseProfile enterprise;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    private CandidateProfile candidate;
}
