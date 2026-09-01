package com.vikisol.arena.rooms.repository;

import com.vikisol.arena.rooms.entity.RoomMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomMemberRepository extends JpaRepository<RoomMember, UUID> {
    @EntityGraph(attributePaths = "room")
    List<RoomMember> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @EntityGraph(attributePaths = "user")
    List<RoomMember> findByRoomId(UUID roomId);

    Optional<RoomMember> findByRoomIdAndUserId(UUID roomId, UUID userId);

    boolean existsByRoomIdAndUserId(UUID roomId, UUID userId);

    // P3 audit fix: RoomService.toResponse(Room,...) used to load every member row just to call
    // .size() on the list, per room, in getMyRooms - a straight COUNT query instead.
    long countByRoomId(UUID roomId);
}
