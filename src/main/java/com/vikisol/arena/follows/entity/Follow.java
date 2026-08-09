package com.vikisol.arena.follows.entity;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.entity.BaseEntity;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import jakarta.persistence.*;
import lombok.*;

// Independent join entity, same shape as marketplace's Bid - NOT enterprise.Membership's shape
// (that enforces a 1:1 unique-per-user relationship, wrong for a many-to-many follow graph).
// Phase C adds company-follow additively (nullable followingCompany alongside the now-nullable
// followingUser, plus a targetType discriminator) rather than a parallel CompanyFollow table -
// same generalize-additively precedent as ModerationItem's contentType/room split. Exactly one
// of followingUser/followingCompany is set, matching targetType - enforced in FollowService, not
// the database (no CHECK constraint, consistent with how PostJoinRequest/ModerationItem also
// leave "which optional FK is populated" to application logic).
@Entity
@Table(name = "arena_follows", uniqueConstraints = {
        @UniqueConstraint(columnNames = {"follower_user_id", "following_user_id"}),
        @UniqueConstraint(columnNames = {"follower_user_id", "following_company_id"})
})
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Follow extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "follower_user_id", nullable = false)
    private User followerUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "following_user_id")
    private User followingUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "following_company_id")
    private EnterpriseProfile followingCompany;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(255) not null default 'USER'")
    @Builder.Default
    private FollowTargetType targetType = FollowTargetType.USER;
}
