package com.vikisol.arena.activities.entity;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.entity.BaseEntity;
import com.vikisol.arena.posts.entity.Post;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// "Remind me before" (FE-API-GAPS row 7): one row per person, activity and lead time. Joiners get
// 24h and 2h reminders automatically when approved (flow §3); they can remove them.
@Entity
@Table(name = "arena_post_reminders", uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "user_id", "minutes_before"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class PostReminder extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(nullable = false)
    private int minutesBefore;

    @Column(nullable = false)
    private Instant remindAt;

    private Instant sentAt;
}
