package com.vikisol.arena.notifications.dto;

// Field-for-field mirror of arena-web's `AppNotification` type.
public record NotificationResponse(
        String id,
        String type,
        String title,
        String body,
        String timestamp,
        boolean read,
        // Row 16 (added): activity | need | job | message | safety; absent when it fits none.
        String category
) {
}
