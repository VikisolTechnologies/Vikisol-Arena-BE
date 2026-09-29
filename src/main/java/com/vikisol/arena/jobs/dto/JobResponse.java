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
        int matchPercentage,
        // Rows 22, 28, 41 (added): onsite | hybrid | remote; entry | mid | senior; the last day
        // to apply (YYYY-MM-DD); the must-haves and nice-to-haves (G22) as text; whether the
        // company's domain is verified (G27); whether the signed-in viewer saved it (absent for
        // a guest).
        String workMode,
        String experienceLevel,
        String deadline,
        List<String> mustHaves,
        List<String> niceToHaves,
        boolean companyVerified,
        Boolean saved
) {
}
