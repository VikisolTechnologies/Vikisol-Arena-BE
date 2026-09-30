package com.vikisol.arena.jobs.service;

import com.vikisol.arena.jobs.dto.JobResponse;
import com.vikisol.arena.jobs.entity.JobPosting;
import com.vikisol.arena.matching.ScoringService;
import com.vikisol.arena.profile.entity.CandidateProfile;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;

@Component
@RequiredArgsConstructor
public class JobMapper {

    private final ScoringService scoringService;

    // What a page of jobs needs beyond the rows themselves, fetched in one query each (see
    // JobService.extrasFor).
    public record Extras(java.util.Map<java.util.UUID, List<com.vikisol.arena.hiring.entity.JobRequirement>> requirements,
                         java.util.Set<java.util.UUID> verifiedTenants, java.util.Set<java.util.UUID> saved, boolean signedIn) {
        public static final Extras NONE = new Extras(java.util.Map.of(), java.util.Set.of(), java.util.Set.of(), false);
    }

    public JobResponse toResponse(JobPosting job, CandidateProfile candidate) {
        return toResponse(job, candidate, Extras.NONE);
    }

    /** matchPercentage is 0 when there's no signed-in candidate to match against (public browse). */
    public JobResponse toResponse(JobPosting job, CandidateProfile candidate, Extras extras) {
        int postedDaysAgo = (int) Duration.between(job.getCreatedAt(), Instant.now()).toDays();
        int matchPercentage = candidate == null ? 0
                : scoringService.computeMatchPercentage(candidate, new HashSet<>(job.getSkills()), job.getIndustry().wireValue());

        return new JobResponse(
                job.getId().toString(),
                job.getTitle(),
                job.getEnterprise().getCompanyName(),
                job.getEnterprise().getLogoEmoji(),
                job.getIndustry().wireValue(),
                job.getLocation(),
                job.isRemote(),
                job.getEmploymentType().wireValue(),
                job.getSalaryMin(),
                job.getSalaryMax(),
                job.getSkills(),
                job.getDescription(),
                Math.max(0, postedDaysAgo),
                matchPercentage,
                job.effectiveWorkMode().name().toLowerCase(),
                job.getExperienceLevel() == null ? null : job.getExperienceLevel().name().toLowerCase(),
                job.getDeadline() == null ? null : job.getDeadline().toString(),
                requirementTexts(extras, job, com.vikisol.arena.hiring.entity.JobRequirement.Kind.MUST),
                requirementTexts(extras, job, com.vikisol.arena.hiring.entity.JobRequirement.Kind.NICE),
                extras.verifiedTenants().contains(job.getEnterprise().getId()),
                extras.signedIn() ? extras.saved().contains(job.getId()) : null
        );
    }

    private static List<String> requirementTexts(Extras extras, JobPosting job, com.vikisol.arena.hiring.entity.JobRequirement.Kind kind) {
        return extras.requirements().getOrDefault(job.getId(), List.of()).stream()
                .filter(r -> r.getKind() == kind).map(com.vikisol.arena.hiring.entity.JobRequirement::getText).toList();
    }
}
