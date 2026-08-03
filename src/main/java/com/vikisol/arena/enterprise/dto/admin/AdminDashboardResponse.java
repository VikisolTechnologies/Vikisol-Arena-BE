package com.vikisol.arena.enterprise.dto.admin;

import java.util.List;

public record AdminDashboardResponse(
        int rangeDays,
        TeamTotals totals,
        List<RecruiterActivity> recruiterActivity,
        int creditsBalance,
        int creditsTotal,
        int creditsSpentInRange
) {
    public record TeamTotals(int postings, int unlocks, int stageMoves, int interviews, int messages) {
    }

    public record RecruiterActivity(
            String userId,
            String name,
            String role,
            int postings,
            int unlocks,
            int stageMoves,
            int interviewsHeld,
            int messagesSent,
            // Average gap between this recruiter's own consecutive stage-move events in the
            // range, in hours - a proxy for "how fast do they move applicants along," not a
            // precise per-application dwell time (that would need correlating each move back to
            // its specific application, which the audit log's free-text target doesn't support).
            // Null when fewer than two stage-move events exist to measure a gap from.
            Double avgHoursBetweenStageMoves
    ) {
    }
}
