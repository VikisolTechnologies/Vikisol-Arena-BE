package com.vikisol.arena.activities.entity;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import com.vikisol.arena.posts.entity.Post;

// Private feedback between a host and a participant after an activity (G12). Only the recipient
// (and the author) can read it; it is never shown publicly or added up into a score.
@Entity
@Table(name = "arena_activity_feedback", uniqueConstraints = @UniqueConstraint(columnNames = {"post_id", "from_user_id", "to_user_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ActivityFeedback extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "from_user_id", nullable = false)
    private User fromUser;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "to_user_id", nullable = false)
    private User toUser;

    // Flow §3 A14: "Would you join again?" plus an optional note. No stars, never public.
    private Boolean joinAgain;

    @Column(length = 500)
    private String text;
}
