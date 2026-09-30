package com.vikisol.arena.hiring.entity;

import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import com.vikisol.arena.jobs.entity.JobPosting;

// A must-have or nice-to-have on a posting (G22). Candidates answer must-haves with evidence;
// recruiters assess that evidence - never a score, never a percentage.
@Entity
@Table(name = "arena_job_requirements")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class JobRequirement extends BaseEntity {

    public enum Kind { MUST, NICE }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "posting_id", nullable = false)
    private JobPosting posting;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 8)
    private Kind kind;

    @Column(nullable = false)
    private int position;

    @Column(nullable = false, length = 120)
    private String text;
}
