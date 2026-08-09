package com.vikisol.arena.rooms.repository;

import com.vikisol.arena.rooms.entity.RoomReport;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.UUID;

public interface RoomReportRepository extends JpaRepository<RoomReport, UUID> {
}
