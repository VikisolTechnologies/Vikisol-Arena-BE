package com.vikisol.arena.enterprise.dto;

import com.vikisol.arena.hiring.dto.HiringDtos.QuestionInput;
import jakarta.validation.Valid;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;

import java.util.List;

// PATCH /enterprise/postings/{id} (FE-API-GAPS row 28): edit a draft or a live posting. Every
// field is optional and only changes when sent; deadline "" removes it. Must-haves and questions
// follow G22/G23: they can't change once a candidate has answered them.
public record UpdatePostingRequest(
        @Size(max = 200, message = "must be at most 200 characters") String title,
        String industry,
        @Size(max = 200, message = "must be at most 200 characters") String location,
        String employmentType,
        String workMode,
        @PositiveOrZero Integer salaryMin,
        @PositiveOrZero Integer salaryMax,
        List<String> skills,
        @Size(max = 10000, message = "must be at most 10000 characters") String description,
        String experienceLevel,
        String deadline,
        List<String> mustHaves,
        List<String> niceToHaves,
        List<@Valid QuestionInput> questions
) {
}
