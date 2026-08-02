package com.vikisol.arena.marketplace.dto;

// `done` mirrors the mock's minimal `Milestone{id,label,done}` shape exactly for wire
// compatibility; `status` carries the fuller Phase-3 lifecycle (pending/in_progress/submitted/
// accepted/rejected) that the mock never had a data model for (AUDIT.md item b).
public record MilestoneResponse(
        String id,
        String label,
        boolean done,
        String status
) {
}
