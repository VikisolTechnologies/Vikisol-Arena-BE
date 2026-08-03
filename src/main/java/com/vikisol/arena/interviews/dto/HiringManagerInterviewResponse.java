package com.vikisol.arena.interviews.dto;

import java.util.List;

// Denormalized display data (candidate/job/company names) alongside the raw InterviewResponse
// shape - a hiring_manager's UI shouldn't need separate profile-gated lookups just to render
// "Interview with X for Y at Z" (those endpoints are RECRUITER/COMPANY_ADMIN-only anyway).
public record HiringManagerInterviewResponse(
        String id,
        String applicationId,
        String candidateName,
        String candidateEmoji,
        String jobTitle,
        String companyName,
        List<InterviewSlotDto> proposedSlots,
        String confirmedSlotId,
        String status,
        String meetingLink,
        String notes,
        InterviewFeedbackDto feedback
) {
}
