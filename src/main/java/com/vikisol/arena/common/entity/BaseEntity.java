package com.vikisol.arena.common.entity;

import jakarta.persistence.*;
import lombok.Data;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.Instant;
import java.util.UUID;

@Data
@MappedSuperclass
@EntityListeners(AuditingEntityListener.class)
public abstract class BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @CreatedDate
    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(nullable = false)
    private Instant updatedAt;

    // ARENA-WEB-AND-SEED.md Part 4 - one flag, inherited by every entity that can surface in the
    // v3 UI, marking a row created by the on-demand demo-content seeder (gated behind
    // ARENA_SEED_MODE) rather than a real user or the original DataSeeder bootstrap. Placed here
    // rather than repeated per-entity so every @Table subclass gets its own `demo_content` column
    // from one migration. Never set via a subclass's own @Builder chain (regular @Builder ignores
    // inherited fields, same as id/createdAt/updatedAt above) - the seeder calls
    // setDemoContent(true) after building, before save().
    @Column(nullable = false)
    private boolean demoContent = false;
}
