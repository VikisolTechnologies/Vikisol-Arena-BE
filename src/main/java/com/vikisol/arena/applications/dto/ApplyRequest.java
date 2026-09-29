package com.vikisol.arena.applications.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

// jobId is the Jenny applyToJob body (JennyArenaWriteBodyContractTest) and stays the only
// required field. The rest are optional extras (FE-API-GAPS row 20): screening answers, a cover
// note, and whether to share CTC with this employer.
public record ApplyRequest(
        @NotBlank(message = "is required") String jobId,
        List<@Valid Answer> answers,
        @Size(max = 2000, message = "must be at most 2000 characters") String coverNote,
        Boolean includeCtc
) {
    public record Answer(@NotNull(message = "is required") UUID questionId,
                         @Size(max = 1000, message = "must be at most 1000 characters") String value) {
    }
}
