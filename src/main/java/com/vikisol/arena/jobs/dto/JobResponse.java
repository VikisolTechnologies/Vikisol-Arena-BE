package com.vikisol.arena.jobs.dto;

import java.util.List;

// Field-for-field mirror of arena-web's `Job` type (the candidate browse view).
public record JobResponse(
        String id,
        String title,
        String company,
        String companyEmoji,
        String industry,
        String location,
        boolean remote,
        String employmentType,
        int salaryMin,
        int salaryMax,
        List<String> skills,
        String description,
        int postedDaysAgo,
        int matchPercentage
) {
}
