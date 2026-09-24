package com.vikisol.arena.profile.entity;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.entity.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "arena_candidate_profiles")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode(callSuper = true)
public class CandidateProfile extends BaseEntity {

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false, unique = true)
    private User user;

    @Column(nullable = false)
    private String name;

    @Column(nullable = false)
    private String avatarEmoji;

    @Column(nullable = false)
    private String title;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private Industry industry;

    @Column(nullable = false)
    private String location;

    @Column(nullable = false)
    private boolean remote;

    @ElementCollection
    @CollectionTable(name = "arena_candidate_skills", joinColumns = @JoinColumn(name = "candidate_id"))
    @Builder.Default
    private List<CandidateSkill> skills = new ArrayList<>();

    @Column(nullable = false)
    private int experienceYears;

    @Column(nullable = false)
    private int rateFloor;

    @ElementCollection(targetClass = OpenTo.class)
    @CollectionTable(name = "arena_candidate_open_to", joinColumns = @JoinColumn(name = "candidate_id"))
    @Enumerated(EnumType.STRING)
    @Column(name = "open_to")
    @Builder.Default
    private List<OpenTo> openTo = new ArrayList<>();

    // Cached, server-computed by CareerHealthService whenever the profile changes - see
    // matching/ScoringService for the single source of truth this is derived from (audit note:
    // avoid recomputing this ad hoc per-screen).
    @Column(nullable = false)
    @Builder.Default
    private int careerHealth = 50;

    @Embedded
    private ConsentSettings consent;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    @Builder.Default
    private AutonomyLevel autonomy = AutonomyLevel.SUPERVISED;

    @Column(columnDefinition = "TEXT")
    private String bio;

    // Canonical Arena CV, independent of any specific application - AUDIT.md flagged the mock as
    // having no real CV artifact anywhere (profile was built entirely from onboarding answers,
    // never an uploaded file). Stored via FileStorageService (local disk today, Cloudinary later).
    private String cvUrl;

    private String cvFileName;

    // ARENA-V2-PRODUCT-ARCHITECTURE.md §5 (Phase B). approxLat/approxLng are ALWAYS a
    // geohash-decoded approximation, never the raw device coordinate - see DECISIONS.md's
    // location entry for the full reasoning. Null geohash = no discovery-center set (OFF
    // consent, or PRECISE/CITY consent granted but no position captured yet either way).
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, columnDefinition = "varchar(255) not null default 'OFF'")
    @Builder.Default
    private LocationConsent locationConsent = LocationConsent.OFF;

    private String homeCity;
    private String geohash;
    private Double approxLat;
    private Double approxLng;

    // Onboarding wizard job-intent branch (see V13 migration) - all nullable, all skippable.
    // null cameForJob = never asked/answered (older accounts, or skipped); true = job seeker
    // (unlocks the Naukri/LinkedIn-style fields below), false = "just here to explore/connect".
    private Boolean cameForJob;
    private String organization;
    private Integer currentCtc;
    private Integer expectedCtc;
    private String preferredLocation;
}
