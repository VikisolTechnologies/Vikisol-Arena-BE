package com.vikisol.arena.interviews.entity;

import com.vikisol.arena.applications.entity.Application;
import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Entity
@Table(name = "arena_interviews")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class Interview extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false, unique = true)
    private Application application;

    @OneToMany(mappedBy = "interview", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @Builder.Default
    private List<InterviewSlot> proposedSlots = new ArrayList<>();

    private UUID confirmedSlotId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private InterviewStatus status = InterviewStatus.PROPOSED;
}
