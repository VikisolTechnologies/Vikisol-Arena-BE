package com.vikisol.arena.rooms.repository;

import com.vikisol.arena.rooms.entity.Room;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;
import java.util.UUID;

public interface RoomRepository extends JpaRepository<Room, UUID> {
    Optional<Room> findByPostId(UUID postId);

    // DemoContentService.removeAll() - two sets, unioned by the caller: rooms this seeder itself
    // flagged, plus any room that got created ORGANICALLY (a real user's approved join on a demo
    // post, via RoomService.getOrCreateForPost) and so was never flagged itself but still needs
    // to go when its post does.
    java.util.List<Room> findByDemoContentTrue();
    java.util.List<Room> findByPost_DemoContentTrue();
}
