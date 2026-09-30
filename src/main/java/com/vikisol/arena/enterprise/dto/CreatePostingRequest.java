package com.vikisol.arena.enterprise.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.PositiveOrZero;

import com.vikisol.arena.hiring.dto.HiringDtos.QuestionInput;

import java.util.List;

public record CreatePostingRequest(
        @NotBlank(message = "is required") String title,
        @NotBlank(message = "is required") String industry,
        @NotBlank(message = "is required") String location,
        boolean remote,
        @NotBlank(message = "is required") String employmentType,
        @NotNull(message = "is required") @PositiveOrZero Integer salaryMin,
        @NotNull(message = "is required") @PositiveOrZero Integer salaryMax,
        @NotNull(message = "is required") List<String> skills,
        @NotBlank(message = "is required") String description,
        // Row 28 (optional extras). status: "draft" saves without publishing (default "open").
        // workMode onsite | hybrid | remote (sets `remote`); experienceLevel entry | mid | senior
        // (the frontend's labels work too); deadline YYYY-MM-DD, today or later; the must-haves,
        // nice-to-haves and screening questions as in G22/G23.
        String status,
        String workMode,
        String experienceLevel,
        String deadline,
        java.util.List<String> mustHaves,
        java.util.List<String> niceToHaves,
        List<@jakarta.validation.Valid QuestionInput> questions
) {
    public CreatePostingRequest(String title, String industry, String location, boolean remote, String employmentType,
                                Integer salaryMin, Integer salaryMax, List<String> skills, String description) {
        this(title, industry, location, remote, employmentType, salaryMin, salaryMax, skills, description,
                null, null, null, null, null, null, null);
    }
}
