package com.vikisol.arena.rooms.entity;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Generalizes Conversation.lastReadAtA/B to N members - one row per (room, user) instead of two
// fixed columns, since a Room is N-ary not 1:1. Unread state per member drives the Rooms list's
// unread indicator, same mechanism as the Messages inbox.
@Entity
@Table(name = "arena_room_members", uniqueConstraints = @UniqueConstraint(columnNames = {"room_id", "user_id"}))
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class RoomMember extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "room_id", nullable = false)
    private Room room;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private RoomMemberRole role = RoomMemberRole.MEMBER;

    private Instant lastReadAt;
}
