package com.vikisol.arena.marketplace.dto;

// `done`/`amount` mirror the mock's `Milestone{id,label,amount,done}` shape exactly for wire
// compatibility; `status` carries the fuller Phase-3 lifecycle (pending/in_progress/submitted/
// accepted/rejected) that the mock never had a data model for (AUDIT.md item b). `deliverable` is
// the most recent submission (null until one exists) - without it the poster's UI has no way to
// see what was actually delivered, or to tell a real submission apart from an empty one.
public record MilestoneResponse(
        String id,
        String label,
        int amount,
        boolean done,
        String status,
        DeliverableResponse deliverable
) {
}
