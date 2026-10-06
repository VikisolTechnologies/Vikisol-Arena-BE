package com.vikisol.arena.activities.entity;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import com.vikisol.arena.posts.entity.Post;

import java.time.Instant;

// A place in a full activity's queue (G9). joinedAt orders the queue; a re-join after leaving
// goes to the back.
@Entity
@Table(name = "arena_activity_waitlist", uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "user_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ActivityWaitlistEntry extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    @Builder.Default
    private WaitlistStatus status = WaitlistStatus.WAITING;

    @Column(nullable = false)
    private Instant joinedAt;

    private Instant promotedAt;
}
