package com.vikisol.arena.projects.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

import java.util.List;
import java.util.UUID;

// Bodies for /projects (G29-G32).
public final class ProjectDtos {

    private ProjectDtos() {
    }

    // count (row 26) is the same as slots; skills (≤8 × ≤40) and hoursPerWeek (1-40) are optional.
    public record RoleInput(
            @NotBlank(message = "is required") @Size(max = 80, message = "must be at most 80 characters") String title,
            @Size(max = 300, message = "must be at most 300 characters") String description,
            @Min(value = 1, message = "must be at least 1") @Max(value = 50, message = "must be at most 50") Integer slots,
            @Min(value = 1, message = "must be at least 1") @Max(value = 50, message = "must be at most 50") Integer count,
            List<String> skills,
            @Min(value = 1, message = "must be at least 1") @Max(value = 40, message = "must be at most 40") Integer hoursPerWeek
    ) {
        public RoleInput(String title, String description, Integer slots) {
            this(title, description, slots, null, null, null);
        }
    }

    // POST /projects (row 26, flow §7 PR1-PR2): the project and its roles in one call.
    // category: community | environment | education | tech | design | arts | other.
    // where: local | remote | both.
    public record CreateProjectRequest(
            @NotBlank(message = "is required") @Size(max = 80, message = "must be at most 80 characters") String title,
            @NotBlank(message = "is required") @Size(max = 2000, message = "must be at most 2000 characters") String goal,
            @NotBlank(message = "is required") String category,
            String where,
            @Min(value = 1, message = "must be at least 1") @Max(value = 52, message = "must be at most 52") Integer weeks,
            List<@Valid RoleInput> roles,
            List<String> tags,
            String locationText
    ) {
    }

    // POST /projects/{id}/applications: the flow's name for joining with a note.
    public record ApplicationRequest(UUID roleId, @Size(max = 500, message = "must be at most 500 characters") String note) {
    }

    public record MilestoneInput(@NotBlank(message = "is required") @Size(max = 120, message = "must be at most 120 characters") String title) {
    }

    public record MilestoneUpdate(@Size(max = 120, message = "must be at most 120 characters") String title, Boolean done) {
    }

    public record MilestoneView(String id, String title, boolean done, String doneAt) {
    }

    // PR6: the outcome, and the team members to name as contributors.
    public record CompleteRequest(
            @NotBlank(message = "is required") @Size(max = 1000, message = "must be at most 1000 characters") String outcome,
            List<UUID> contributorIds
    ) {
    }

    public record RolesRequest(@NotNull(message = "is required") List<@Valid RoleInput> roles) {
    }

    public record JoinRequest(UUID roleId, @Size(max = 500, message = "must be at most 500 characters") String message) {
    }

    // skills and hoursPerWeek added (row 26).
    public record RoleView(String id, String title, String description, int slots, long filled, List<String> skills, Integer hoursPerWeek) {
    }

    public record MemberView(String userId, String name, String avatarEmoji, String roleId, String roleTitle) {
    }

    // GET /projects/{id}. `team` is the people who are in; requests stay with the owner.
    // goal and later fields added (row 26). outcome, completedAt and contributors appear once
    // the project is completed.
    public record ProjectView(String postId, String title, String status, List<RoleView> roles, List<MemberView> team, Viewer viewer,
                              String goal, String category, String where, Integer weeks, String coverUrl,
                              String outcome, String completedAt, List<MemberView> contributors) {
    }

    public record Viewer(boolean owner, String joinStatus, String roleId) {
    }

    // Owner's list of who asked for what.
    public record RequestView(String joinId, String userId, String name, String status, String roleId, String roleTitle, String message) {
    }

    // outcome, completedAt and contributor added (row 26): a completed project on a profile.
    public record ProjectCard(String postId, String title, String status, String role, String createdAt,
                              String outcome, String completedAt, boolean contributor) {
    }

    // G32: the profile stat row. Real counts only.
    public record ProfileStats(long hosted, long joined, long helped, long projects) {
    }
}
