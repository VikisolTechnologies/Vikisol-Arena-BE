package com.vikisol.arena.rooms.repository;

import com.vikisol.arena.rooms.entity.RoomMember;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface RoomMemberRepository extends JpaRepository<RoomMember, UUID> {
    // The caller's rooms, most recent activity first: the latest message, or when they joined if
    // nobody has written yet. room.post is fetched because RoomService.getMyRooms reads the
    // post's body/intent/status for every room. id breaks ties so page boundaries are stable.
    @Query(value = """
            select rm from RoomMember rm join fetch rm.room r join fetch r.post
            where rm.user.id = :userId
            order by coalesce((select max(m.createdAt) from RoomMessage m where m.room = r), rm.createdAt) desc, rm.id desc
            """,
            countQuery = "select count(rm) from RoomMember rm where rm.user.id = :userId")
    Page<RoomMember> findMyRoomsByLatestActivity(@Param("userId") UUID userId, Pageable pageable);

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
