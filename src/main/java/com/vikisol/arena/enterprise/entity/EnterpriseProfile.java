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
}
