package com.vikisol.arena.enterprise.entity;

import com.vikisol.arena.common.entity.BaseEntity;
import com.vikisol.arena.profile.entity.CandidateProfile;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "arena_shortlist_entries", uniqueConstraints = @UniqueConstraint(columnNames = {"enterprise_id", "candidate_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ShortlistEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enterprise_id", nullable = false)
    private EnterpriseProfile enterprise;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "candidate_id", nullable = false)
    private CandidateProfile candidate;
}
