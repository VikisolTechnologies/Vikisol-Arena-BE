package com.vikisol.arena.rooms.repository;

import com.vikisol.arena.rooms.entity.RoomMessage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
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

    // Batched findTopByRoomIdOrderByCreatedAtDesc() for a whole room list. Two messages with the
    // exact same timestamp in one room would both come back; the caller keeps one.
    @Query("""
            select m from RoomMessage m
            where m.room.id in :roomIds
              and m.createdAt = (select max(m2.createdAt) from RoomMessage m2 where m2.room.id = m.room.id)
            """)
    List<RoomMessage> findLatestByRoomIdIn(@Param("roomIds") Collection<UUID> roomIds);

    // DemoContentService.removeAll() - a room's messages have to go before the room itself (FK).
    void deleteByRoomId(UUID roomId);
}
