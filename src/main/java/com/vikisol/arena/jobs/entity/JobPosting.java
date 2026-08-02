package com.vikisol.arena.jobs.entity;

import com.vikisol.arena.common.entity.BaseEntity;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.profile.entity.Industry;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

// Single entity backing both the candidate-facing `Job` browse listing and the enterprise-facing
// `JobPosting` management view in types.ts - AUDIT.md's "unify parallel models" principle applies
// here too (it was explicitly called out for Applicant/Application; the same duplication risk
// existed for Job/JobPosting so it's collapsed the same way: one entity, two DTOs/views).
@Entity
@Table(name = "arena_job_postings")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class JobPosting extends BaseEntity {

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "enterprise_id", nullable = false)
    private EnterpriseProfile enterprise;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Industry industry;

    @Column(nullable = false)
    private String location;

    @Column(nullable = false)
    private boolean remote;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private EmploymentType employmentType;

    @Column(nullable = false)
    private int salaryMin;

    @Column(nullable = false)
    private int salaryMax;

    @ElementCollection
    @CollectionTable(name = "arena_job_posting_skills", joinColumns = @JoinColumn(name = "posting_id"))
    @Column(name = "skill")
    @Builder.Default
    private List<String> skills = new ArrayList<>();

    @Column(columnDefinition = "TEXT", nullable = false)
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private PostingStatus status = PostingStatus.OPEN;
}
