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
        List<CompanyResponse> companies,
        // Row 17 (added): people, for type=people or type=skills and a signed-in searcher only.
        List<PersonResult> people
) {
    // distanceBand: a coarse band ("within 2 km"), never the exact distance - ARCHITECT-REVIEW-BE-1
    // blocker #1: an exact distance from an attacker-controlled point lets someone triangulate a
    // person's real location with a few searches. Only set when the search was near the viewer's
    // own stored location and the person shares theirs.
    public record PersonResult(String userId, String name, String avatarEmoji, String photoUrl, String title,
                               List<String> skills, List<String> interests, String distanceBand) {
    }
}
