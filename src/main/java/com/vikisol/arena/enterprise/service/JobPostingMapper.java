package com.vikisol.arena.enterprise.service;

import com.vikisol.arena.enterprise.dto.JobPostingResponse;
import com.vikisol.arena.jobs.entity.JobPosting;
import org.springframework.stereotype.Component;

@Component
public class JobPostingMapper {

    public JobPostingResponse toResponse(JobPosting job) {
        return new JobPostingResponse(
                job.getId().toString(), job.getTitle(), job.getIndustry().wireValue(), job.getLocation(),
                job.isRemote(), job.getEmploymentType().wireValue(), job.getSalaryMin(), job.getSalaryMax(),
                job.getSkills(), job.getDescription(), job.getStatus().wireValue(), job.getCreatedAt().toString());
    }
}
