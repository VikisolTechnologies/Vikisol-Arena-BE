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

@Component
@RequiredArgsConstructor
public class JobMapper {

    private final ScoringService scoringService;

    /** matchPercentage is 0 when there's no signed-in candidate to match against (public browse). */
    public JobResponse toResponse(JobPosting job, CandidateProfile candidate) {
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
                matchPercentage
        );
    }
}
