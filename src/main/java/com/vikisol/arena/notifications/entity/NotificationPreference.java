package com.vikisol.arena.notifications.entity;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

// Which notification categories someone wants (FE-API-GAPS row 18). Safety notices can't be
// turned off. No row = everything on.
@Entity
@Table(name = "arena_notification_preferences")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class NotificationPreference extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    @Builder.Default
    private boolean activity = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean need = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean job = true;

    @Column(nullable = false)
    @Builder.Default
    private boolean message = true;
}
