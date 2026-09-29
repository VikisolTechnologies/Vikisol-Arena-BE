package com.vikisol.arena.needs.entity;

import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

// Both sides of an accepted response confirm it happened (G16). It is an outcome only once both
// have: completedAt is set when the second confirmation arrives. Notes are seen only by the two.
@Entity
@Table(name = "arena_need_completions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class NeedCompletion extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "response_id", nullable = false, unique = true)
    private NeedResponse response;

    private Instant ownerConfirmedAt;
    private Instant responderConfirmedAt;

    @Column(length = 500)
    private String ownerNote;

    @Column(length = 500)
    private String responderNote;

    private Instant completedAt;
}
