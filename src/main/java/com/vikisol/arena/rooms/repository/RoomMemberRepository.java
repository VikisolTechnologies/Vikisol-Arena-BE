package com.vikisol.arena.rooms.repository;

import com.vikisol.arena.rooms.entity.RoomMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomMemberRepository extends JpaRepository<RoomMember, UUID> {
    // room.post too: RoomService.getMyRooms reads the post's body/intent/status for every room.
    @EntityGraph(attributePaths = {"room", "room.post"})
    List<RoomMember> findByUserIdOrderByCreatedAtDesc(UUID userId);

    @EntityGraph(attributePaths = "user")
    List<RoomMember> findByRoomId(UUID roomId);

    Optional<RoomMember> findByRoomIdAndUserId(UUID roomId, UUID userId);

    boolean existsByRoomIdAndUserId(UUID roomId, UUID userId);

    // P3 audit fix, then batched: RoomService.getMyRooms used to load every member row per room
    // just to count them, then one COUNT per room - now one GROUP BY for the whole list.
    @Query("select m.room.id as roomId, count(m) as cnt from RoomMember m where m.room.id in :roomIds group by m.room.id")
    List<RoomCount> countByRoomIdIn(@Param("roomIds") Collection<UUID> roomIds);

    interface RoomCount {
        UUID getRoomId();
        long getCnt();
    }

    // RoomService.deleteRoomForPostIfEmpty() - only ever reached for a room with zero messages,
    // but members can join before anyone sends the first one.
    void deleteByRoomId(UUID roomId);
}
