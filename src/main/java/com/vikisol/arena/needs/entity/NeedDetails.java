package com.vikisol.arena.needs.entity;

import com.vikisol.arena.common.entity.BaseEntity;
import com.vikisol.arena.posts.entity.Post;
import jakarta.persistence.*;
import lombok.*;

// Category and preferred time for an ASK (need) or OFFER post (G14).
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
}
