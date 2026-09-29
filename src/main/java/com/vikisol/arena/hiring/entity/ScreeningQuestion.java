package com.vikisol.arena.hiring.entity;

import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import com.vikisol.arena.jobs.entity.JobPosting;

// A question every applicant to a posting answers (G23).
@Entity
@Table(name = "arena_job_screening_questions")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class ScreeningQuestion extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posting_id", nullable = false)
    private JobPosting posting;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 200)
    private String text;

    @Column(nullable = false)
    @Builder.Default
    private boolean required = true;
}
