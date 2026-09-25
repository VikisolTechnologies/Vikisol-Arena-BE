package com.vikisol.arena.search;

import com.vikisol.arena.company.dto.CompanyResponse;
import com.vikisol.arena.jobs.dto.JobResponse;
import com.vikisol.arena.marketplace.dto.ProjectResponse;
import com.vikisol.arena.posts.dto.PostResponse;

import java.util.List;

// One response per search, grouped by kind so the client can show "All" as sections and each
// tab as a plain list - same DTOs every other screen already renders.
public record SearchResponse(
        String query,
        List<PostResponse> activities,
        List<PostResponse> discussions,
        List<JobResponse> jobs,
        List<ProjectResponse> projects,
        List<CompanyResponse> companies
) {
}
