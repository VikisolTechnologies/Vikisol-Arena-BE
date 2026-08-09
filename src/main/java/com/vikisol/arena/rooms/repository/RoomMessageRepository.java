package com.vikisol.arena.rooms.repository;

import com.vikisol.arena.rooms.entity.RoomMessage;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface RoomMessageRepository extends JpaRepository<RoomMessage, UUID> {
    @EntityGraph(attributePaths = "sender")
    List<RoomMessage> findByRoomIdOrderByCreatedAtAsc(UUID roomId);
}
