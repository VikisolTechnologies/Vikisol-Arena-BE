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

    public record RoleInput(
            @NotBlank(message = "is required") @Size(max = 80, message = "must be at most 80 characters") String title,
            @Size(max = 300, message = "must be at most 300 characters") String description,
            @Min(value = 1, message = "must be at least 1") @Max(value = 50, message = "must be at most 50") Integer slots
    ) {
    }

    public record RolesRequest(@NotNull(message = "is required") List<@Valid RoleInput> roles) {
    }

    public record JoinRequest(UUID roleId, @Size(max = 500, message = "must be at most 500 characters") String message) {
    }

    public record RoleView(String id, String title, String description, int slots, long filled) {
    }

    public record MemberView(String userId, String name, String avatarEmoji, String roleId, String roleTitle) {
    }

    // GET /projects/{id}. `team` is the people who are in; requests stay with the owner.
    public record ProjectView(String postId, String title, String status, List<RoleView> roles, List<MemberView> team, Viewer viewer) {
    }

    public record Viewer(boolean owner, String joinStatus, String roleId) {
    }

    // Owner's list of who asked for what.
    public record RequestView(String joinId, String userId, String name, String status, String roleId, String roleTitle, String message) {
    }

    public record ProjectCard(String postId, String title, String status, String role, String createdAt) {
    }

    // G32: the profile stat row. Real counts only.
    public record ProfileStats(long hosted, long joined, long helped, long projects) {
    }
}
