package com.vikisol.arena.interviews.dto;

import java.util.List;

// Field-for-field mirror of arena-web's `Interview` type.
public record InterviewResponse(
        String id,
        String applicationId,
        List<InterviewSlotDto> proposedSlots,
        String confirmedSlotId,
        String status
) {
}
