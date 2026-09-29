package com.vikisol.arena.career.entity;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.career.entity.CareerEnums.*;
import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

// The opt-in career layer on one Arena identity (G18-G21): no second signup, nothing visible to
// anyone else until published. Skills and the CV stay on CandidateProfile (one place for each).
@Entity
@Table(name = "arena_career_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CareerProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Intent intent;

    @Column(length = 100)
    private String desiredRole;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private ExperienceLevel experienceLevel;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private WorkMode workMode = WorkMode.ANY;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private NoticePeriod noticePeriod;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    @Builder.Default
    private CompensationVisibility compensationVisibility = CompensationVisibility.PRIVATE;

    // Expected yearly compensation in INR.
    private Integer expectedMin;
    private Integer expectedMax;

    @Column(nullable = false)
    @Builder.Default
    private boolean openToWork = false;

    // Null = not published: nobody else sees any of it.
    private Instant publishedAt;

    // Flow §6 extras (row 19), V33. Pay columns follow the same rule as expectedMin/Max: private,
    // shared only on an application with "include my CTC".
    @Column(length = 80)
    private String currentCompany;

    @Enumerated(EnumType.STRING)
    @Column(length = 16)
    private WorkStatus workStatus;

    private java.time.LocalDate lastWorkingDay;

    private Integer experienceMonths;

    @Column(length = 20)
    private String roleFamily;

    private Integer currentCtcFixed;
    private Integer currentCtcVariable;
    private Boolean negotiable;

    // CareerDtos.CareerDetails' list and text fields, as JSON.
    @Column(nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String detailsJson = "{}";

    // { field: only_me | employers_i_apply | public }; missing fields use the defaults in
    // CareerService.
    @Column(nullable = false, columnDefinition = "TEXT")
    @Builder.Default
    private String visibilityJson = "{}";

    @ElementCollection
    @CollectionTable(name = "arena_career_locations", joinColumns = @JoinColumn(name = "career_id"))
    @Column(name = "location")
    @Builder.Default
    private List<String> preferredLocations = new ArrayList<>();
}
