package com.vikisol.arena.enterprise.dto;

import java.util.List;

// Field-for-field mirror of arena-web's `JobPosting` type (the enterprise management view of
// the same JobPosting entity the candidate side sees as `Job` - see jobs/dto/JobResponse).
public record JobPostingResponse(
        String id,
        String title,
        String industry,
        String location,
        boolean remote,
        String employmentType,
        int salaryMin,
        int salaryMax,
        List<String> skills,
        String description,
        String status,
        String createdAt
) {
}
