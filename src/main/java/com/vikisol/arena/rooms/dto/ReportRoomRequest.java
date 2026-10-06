package com.vikisol.arena.rooms.dto;

import jakarta.validation.constraints.NotBlank;

// evidenceUrls (row 15, optional): up to 4 files uploaded with POST /reports/evidence.
public record ReportRoomRequest(@NotBlank(message = "is required") String reason, java.util.List<String> evidenceUrls) {
    public ReportRoomRequest(String reason) {
        this(reason, null);
    }

}
