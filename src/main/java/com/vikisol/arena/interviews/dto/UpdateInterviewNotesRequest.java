package com.vikisol.arena.interviews.dto;

// Notes are free-form and may legitimately be saved as blank (the UI's onBlur handler writes
// whatever's currently in the textarea) - no @NotBlank here, unlike ConfirmSlotRequest/AdvanceStageRequest.
public record UpdateInterviewNotesRequest(String notes) {
}
