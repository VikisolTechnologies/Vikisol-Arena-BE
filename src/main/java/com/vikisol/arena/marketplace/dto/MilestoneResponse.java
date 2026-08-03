package com.vikisol.arena.marketplace.dto;

// `done`/`amount` mirror the mock's `Milestone{id,label,amount,done}` shape exactly for wire
// compatibility; `status` carries the fuller Phase-3 lifecycle (pending/in_progress/submitted/
// accepted/rejected) that the mock never had a data model for (AUDIT.md item b).
public record MilestoneResponse(
        String id,
        String label,
        int amount,
        boolean done,
        String status
) {
}
