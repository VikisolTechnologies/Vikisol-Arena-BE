package com.vikisol.arena.posts.dto;

import jakarta.validation.constraints.NotBlank;

// evidenceUrls (row 15, optional): up to 4 files uploaded with POST /reports/evidence.
public record ReportPostRequest(@NotBlank(message = "is required") String reason, java.util.List<String> evidenceUrls) {
    public ReportPostRequest(String reason) {
        this(reason, null);
    }

}
