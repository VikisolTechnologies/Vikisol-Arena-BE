package com.vikisol.arena.applications.dto;

// FE-API-GAPS row 30. actorName is the person on the company side ("You" is not assumed); it is
// absent for the candidate's own actions.
public record ApplicationTimeline(String type, String stage, String actorName, String message, String at) {
}
