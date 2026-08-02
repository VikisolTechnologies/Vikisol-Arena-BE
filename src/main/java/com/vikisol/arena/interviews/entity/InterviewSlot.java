package com.vikisol.arena.interviews.entity;

import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

@Entity
@Table(name = "arena_interview_slots")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class InterviewSlot extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "interview_id", nullable = false)
    private Interview interview;

    @Column(nullable = false)
    private Instant start;

    @Column(nullable = false)
    private int durationMinutes;
}
