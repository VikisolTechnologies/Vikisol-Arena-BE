package com.vikisol.arena.needs.entity;

import com.vikisol.arena.common.entity.BaseEntity;
import com.vikisol.arena.posts.entity.Post;
import jakarta.persistence.*;
import lombok.*;

// Category and preferred time for an ASK (need) or OFFER post (G14), plus the flow §4 intake
// (row 27): urgency, help type and category answers; for an offer, its days, limit and proof link.
@Entity
@Table(name = "arena_need_details")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class NeedDetails extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false, unique = true)
    private Post post;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private NeedCategory category;

    @Column(length = 100)
    private String preferredTime;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private NeedIntake.Urgency urgency;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private NeedIntake.HelpType helpType;

    @Column(nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String answersJson = "{}";

    @Column(nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String offerDaysJson = "[]";

    @Enumerated(EnumType.STRING)
    @Column(length = 32)
    private NeedIntake.OfferLimit offerLimit;

    @Column(length = 500)
    private String proofUrl;
}
