package com.vikisol.arena.enterprise.dto;

import com.vikisol.arena.profile.dto.CandidateProfileResponse;

// Field-for-field mirror of arena-web's `Applicant` type, plus the embedded candidate object
// getApplicantsForPosting() returns alongside it - backed by the same Application row the
// candidate sees (see applications/entity/Application), not a separately-seeded parallel record.
public record ApplicantResponse(
        String id,
        String postingId,
        String candidateId,
        String stage,
        String appliedAt,
        CandidateProfileResponse candidate,
        // Row 32 (added): the applicant's career profile as this employer sees it - noticePeriod,
        // and each extra field the person shares with employers they apply to; pay only when
        // this application includes it. Absent when they have no career profile.
        com.vikisol.arena.career.dto.CareerDtos.CareerPublicView career
) {
}
