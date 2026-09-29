package com.vikisol.arena.projects.service;

import com.vikisol.arena.activities.ActivityRules;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.common.policy.ProtectedAttributes;
import com.vikisol.arena.needs.repository.NeedCompletionRepository;
import com.vikisol.arena.posts.dto.PostJoinRequestResponse;
import com.vikisol.arena.posts.entity.*;
import com.vikisol.arena.posts.repository.PostJoinRequestRepository;
import com.vikisol.arena.posts.repository.PostRepository;
import com.vikisol.arena.posts.service.PostService;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import com.vikisol.arena.projects.dto.ProjectDtos.*;
import com.vikisol.arena.projects.entity.ProjectMember;
import com.vikisol.arena.projects.entity.ProjectRole;
import com.vikisol.arena.projects.repository.ProjectMemberRepository;
import com.vikisol.arena.projects.repository.ProjectRoleRepository;
import com.vikisol.arena.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

// Community projects (G29-G31): a COLLAB post with open roles. Joining is the normal post join
// (approval, room, leaving), so the team space is the post's Room - no second system. This is
// separate from the paid marketplace (/marketplace/projects: bids, award, milestones).
@Service
@RequiredArgsConstructor
public class CollabProjectService {

    static final int MAX_ROLES = 10;

    private final PostRepository postRepository;
    private final PostJoinRequestRepository joinRepository;
    private final PostService postService;
    private final ProjectRoleRepository roleRepository;
    private final ProjectMemberRepository memberRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final UserRepository userRepository;
    private final NeedCompletionRepository completionRepository;

    @Transactional(readOnly = true)
    public ProjectView get(UUID postId, UUID viewerId) {
        return toView(requireProject(postId), viewerId);
    }

    @Transactional
    public ProjectView setRoles(UUID ownerId, UUID postId, List<RoleInput> roles) {
        Post post = requireOwnedProject(ownerId, postId);
        if (roles.size() > MAX_ROLES) throw new BadRequestException("A project can have at most " + MAX_ROLES + " open roles");
        if (memberRepository.existsByRolePostId(postId)) {
            throw new BadRequestException("People have already asked for these roles, so they can't change now");
        }
        for (RoleInput r : roles) {
            ProtectedAttributes.reject("the role", r.title());
            ProtectedAttributes.reject("the role", r.description());
        }
        roleRepository.deleteByPostId(postId);
        roleRepository.flush();
        int position = 0;
        for (RoleInput r : roles) {
            roleRepository.save(ProjectRole.builder().post(post).position(position++).title(r.title().trim())
                    .description(r.description() == null || r.description().isBlank() ? null : r.description().trim())
                    .slots(r.slots() == null ? 1 : r.slots()).build());
        }
        return toView(post, ownerId);
    }

    // Join, optionally for a role. The request, approval and room are PostService's.
    @Transactional
    public PostJoinRequestResponse join(UUID userId, UUID postId, JoinRequest request) {
        Post post = requireProject(postId);
        ProjectRole role = null;
        List<ProjectRole> roles = roleRepository.findByPostIdOrderByPositionAsc(postId);
        if (request.roleId() != null) {
            role = roles.stream().filter(r -> r.getId().equals(request.roleId())).findFirst()
                    .orElseThrow(() -> new BadRequestException("That role isn't on this project"));
            if (memberRepository.countApprovedInRole(role.getId()) >= role.getSlots()) {
                throw new BadRequestException("That role is already filled");
            }
        } else if (!roles.isEmpty()) {
            throw new BadRequestException("Pick the role you'd like to take");
        }
        PostJoinRequestResponse joined = postService.requestJoin(userId, postId, true, null);
        ProjectMember member = memberRepository.findByPostIdAndUserId(postId, userId)
                .orElseGet(() -> ProjectMember.builder().post(post).user(userRepository.getReferenceById(userId)).build());
        member.setRole(role);
        member.setMessage(request.message() == null || request.message().isBlank() ? null : request.message().trim());
        memberRepository.save(member);
        return joined;
    }

    // Owner-only: every request with the role asked for and the note. Approve/decline with the
    // existing PUT /posts/{id}/joins/{joinId}/approve|decline.
    @Transactional(readOnly = true)
    public List<RequestView> requests(UUID ownerId, UUID postId) {
        requireOwnedProject(ownerId, postId);
        Map<UUID, ProjectMember> members = memberRepository.findByPostId(postId).stream()
                .collect(Collectors.toMap(m -> m.getUser().getId(), Function.identity()));
        Map<UUID, CandidateProfile> profiles = candidateProfileRepository.mapByUserId(members.keySet());
        List<PostJoinRequest> joins = joinRepository.findByPostIdOrderByCreatedAtAscIdAsc(postId, Pageable.unpaged()).getContent();
        return joins.stream().filter(j -> j.getStatus() != PostJoinStatus.WITHDRAWN).map(j -> {
            ProjectMember m = members.get(j.getUser().getId());
            CandidateProfile p = profiles.get(j.getUser().getId());
            return new RequestView(j.getId().toString(), j.getUser().getId().toString(), p != null ? p.getName() : j.getUser().getName(),
                    j.getStatus().wireValue(), m == null || m.getRole() == null ? null : m.getRole().getId().toString(),
                    m == null || m.getRole() == null ? null : m.getRole().getTitle(), m == null ? null : m.getMessage());
        }).toList();
    }

    @Transactional(readOnly = true)
    public Page<ProjectCard> projectsOf(UUID userId, Pageable pageable) {
        Page<Post> page = postRepository.findCollabProjectsOf(userId, pageable);
        return page.map(p -> {
            String role = p.getAuthorUser().getId().equals(userId) ? "owner"
                    : memberRepository.findByPostIdAndUserId(p.getId(), userId).map(ProjectMember::getRole).map(ProjectRole::getTitle).orElse("member");
            return new ProjectCard(p.getId().toString(), title(p), p.getStatus().wireValue(), role, p.getCreatedAt().toString());
        });
    }

    // G32: the profile's Hosted / Joined / Helped / Projects row.
    @Transactional(readOnly = true)
    public ProfileStats stats(UUID userId) {
        userRepository.findById(userId).filter(u -> u.getDeletedAt() == null)
                .orElseThrow(() -> new ResourceNotFoundException("Profile not found"));
        return new ProfileStats(
                postRepository.countHostedActivities(userId),
                joinRepository.countJoinedActivities(userId, Instant.now().minus(ActivityRules.DISPUTE_WINDOW)),
                completionRepository.countHelped(userId),
                postRepository.findCollabProjectsOf(userId, Pageable.ofSize(1)).getTotalElements());
    }

    private ProjectView toView(Post post, UUID viewerId) {
        List<ProjectRole> roles = roleRepository.findByPostIdOrderByPositionAsc(post.getId());
        List<ProjectMember> members = memberRepository.findByPostId(post.getId());
        Set<UUID> approved = joinRepository.findByPostIdAndStatusOrderByCreatedAtAscIdAsc(post.getId(), PostJoinStatus.APPROVED)
                .stream().map(j -> j.getUser().getId()).collect(Collectors.toSet());
        Map<UUID, CandidateProfile> profiles = candidateProfileRepository.mapByUserId(approved);
        Map<UUID, ProjectMember> byUser = members.stream().collect(Collectors.toMap(m -> m.getUser().getId(), Function.identity()));
        List<MemberView> team = approved.stream().map(uid -> {
            ProjectMember m = byUser.get(uid);
            CandidateProfile p = profiles.get(uid);
            return new MemberView(uid.toString(), p != null ? p.getName() : (m != null ? m.getUser().getName() : "Member"),
                    p != null ? p.getAvatarEmoji() : "🧑🏽",
                    m == null || m.getRole() == null ? null : m.getRole().getId().toString(),
                    m == null || m.getRole() == null ? null : m.getRole().getTitle());
        }).sorted(Comparator.comparing(MemberView::name)).toList();
        List<RoleView> roleViews = roles.stream().map(r -> new RoleView(r.getId().toString(), r.getTitle(), r.getDescription(), r.getSlots(),
                members.stream().filter(m -> m.getRole() != null && m.getRole().getId().equals(r.getId()) && approved.contains(m.getUser().getId())).count()))
                .toList();
        Viewer viewer = null;
        if (viewerId != null) {
            String joinStatus = joinRepository.findByPostIdAndUserId(post.getId(), viewerId)
                    .filter(j -> j.getStatus() != PostJoinStatus.WITHDRAWN).map(j -> j.getStatus().wireValue()).orElse(null);
            ProjectMember mine = byUser.get(viewerId);
            viewer = new Viewer(post.getAuthorUser().getId().equals(viewerId), joinStatus,
                    mine == null || mine.getRole() == null ? null : mine.getRole().getId().toString());
        }
        return new ProjectView(post.getId().toString(), title(post), post.getStatus().wireValue(), roleViews, team, viewer);
    }

    private static String title(Post p) {
        String t = p.getTitle() != null ? p.getTitle() : p.getBody();
        return t.length() > 80 ? t.substring(0, 80) + "…" : t;
    }

    private Post requireProject(UUID postId) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new ResourceNotFoundException("Project not found: " + postId));
        if (post.getIntentType() != PostIntentType.COLLAB || post.isAnonymous() || post.getRemovedReason() != null) {
            throw new ResourceNotFoundException("Project not found: " + postId);
        }
        return post;
    }

    private Post requireOwnedProject(UUID ownerId, UUID postId) {
        Post post = requireProject(postId);
        if (!post.getAuthorUser().getId().equals(ownerId)) throw new AccessDeniedException("Not your project");
        return post;
    }
}
