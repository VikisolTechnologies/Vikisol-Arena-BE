package com.vikisol.arena.rooms.repository;

import com.vikisol.arena.rooms.entity.RoomMessage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomMessageRepository extends JpaRepository<RoomMessage, UUID> {
    // P3 audit fix: RoomService.getMessages used to load a room's ENTIRE history unbounded - a
    // long-running activity room could return thousands of rows on every open. Capped at the
    // 100 most recent (service reverses to ascending for display) rather than a full pagination
    // rework of getRoomMessages' frontend contract - a group activity chat realistically never
    // needs "page 47 of history," just "don't return everything."
    @EntityGraph(attributePaths = "sender")
    List<RoomMessage> findTop100ByRoomIdOrderByCreatedAtDesc(UUID roomId);

    // P3 audit fix: RoomService.toResponse(Room,...) used to load the full message history per
    // room in getMyRooms just to read the single last element - this replaces that with exactly
    // the one row actually needed.
    Optional<RoomMessage> findTopByRoomIdOrderByCreatedAtDesc(UUID roomId);
}
