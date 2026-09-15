package com.vikisol.arena.notifications.repository;

import com.vikisol.arena.notifications.entity.Notification;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.UUID;

public interface NotificationRepository extends JpaRepository<Notification, UUID> {
    Page<Notification> findByUserIdOrderByCreatedAtDesc(UUID userId, Pageable pageable);

    // Bulk "mark all read" in one statement, rather than the frontend firing N PUT
    // /notifications/{id}/read requests for a "mark all read" action.
    @Modifying
    @Query("UPDATE Notification n SET n.read = true WHERE n.user.id = :userId AND n.read = false")
    int markAllReadForUser(@Param("userId") UUID userId);

    // DemoContentService.removeAll().
    void deleteByDemoContentTrue();

    // Real FK gap found live (2026-09-15): deleteByDemoContentTrue() above only catches
    // notifications the seed's own seedNotifications() step wrote - a notification a demo user
    // received through a normal, non-seed code path (e.g. NotificationService.notify() firing
    // off a real follow/join-approval/comment-reply during testing) never gets demoContent=true,
    // so it survived and blocked deleting the user it points at. This catches every notification
    // FOR a demo user regardless of how it was created, by the required user_id FK instead.
    void deleteByUserId(UUID userId);
}
