package com.vikisol.arena.notifications.entity;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Table(name = "arena_notifications")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Notification extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    // MARATHON-BE-2 step 1b: who the notification is about, when it names a specific person -
    // null for notifications with no bound person's name in the body. Lets erase() scrub a
    // deleted person's name out of exactly the rows about them, never a substring match over
    // every notification in the table.
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_user_id")
    private User actorUser;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationType type;

    @Column(nullable = false)
    private String title;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String body;

    @Column(nullable = false)
    @Builder.Default
    private boolean read = false;

    // Row 16 (V37): activity | need | job | message | safety, or null when it fits none; and a
    // snooze - hidden from the list until then.
    @Column(length = 16)
    private String category;

    private java.time.Instant snoozedUntil;
}
