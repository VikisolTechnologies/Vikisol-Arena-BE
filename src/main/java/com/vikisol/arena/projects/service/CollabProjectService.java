package com.vikisol.arena.projects.service;

import com.vikisol.arena.activities.ActivityRules;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.common.intake.IntakeAnswers;
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
import com.vikisol.arena.projects.entity.*;
import com.vikisol.arena.projects.repository.*;
import com.vikisol.arena.auth.entity.User;
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
    private final ProjectDetailsRepository detailsRepository;
    private final ProjectMilestoneRepository milestoneRepository;
    private final ProjectContributorRepository contributorRepository;
    private final com.vikisol.arena.notifications.service.NotificationService notificationService;
    private final com.vikisol.arena.common.service.FileStorageService fileStorageService;
    private final com.vikisol.arena.common.service.FileSigningService fileSigningService;
    private final com.fasterxml.jackson.databind.ObjectMapper objectMapper;
    private final com.vikisol.arena.profile.service.ProfileVisibilityGuard visibilityGuard;

    static final int MAX_MILESTONES = 30;

    @Transactional(readOnly = true)
    public ProjectView get(UUID postId, UUID viewerId) {
        return toView(requireProject(postId), viewerId);
    }

    @Transactional
    public ProjectView setRoles(UUID ownerId, UUID postId, List<RoleInput> roles) {
        Post post = requireOwnedProject(ownerId, postId);
        if (memberRepository.existsByRolePostId(postId)) {
            throw new BadRequestException("People have already asked for these roles, so they can't change now");
        }
        saveRoles(post, roles);
        return toView(post, ownerId);
    }

    private void saveRoles(Post post, List<RoleInput> roles) {
        if (roles.size() > MAX_ROLES) throw new BadRequestException("A project can have at most " + MAX_ROLES + " open roles");
        List<ProjectRole> rows = new ArrayList<>();
        int position = 0;
        for (RoleInput r : roles) {
            ProtectedAttributes.reject("the role", r.title());
            ProtectedAttributes.reject("the role", r.description());
            List<String> skills = IntakeAnswers.cleanList(r.skills(), 8, 40, "skills", "the role");
            rows.add(ProjectRole.builder().post(post).position(position++).title(r.title().trim())
                    .description(r.description() == null || r.description().isBlank() ? null : r.description().trim())
                    .slots(r.slots() != null ? r.slots() : r.count() != null ? r.count() : 1)
                    .skillsJson(writeJson(skills)).hoursPerWeek(r.hoursPerWeek()).build());
        }
        roleRepository.deleteByPostId(post.getId());
        roleRepository.flush();
        roleRepository.saveAll(rows);
    }

    // --- row 26 / flow §7 ------------------------------------------------------------------

    // PR1-PR2: the project (a COLLAB post, approval-only so people apply), its details and roles in
    // one transaction.
    @Transactional
    public ProjectView create(UUID ownerId, CreateProjectRequest r) {
        ProjectDetails.Category category = parse(ProjectDetails.Category.class, r.category(), "category");
        ProjectDetails.Where where = r.where() == null || r.where().isBlank() ? ProjectDetails.Where.LOCAL
                : parse(ProjectDetails.Where.class, r.where(), "where");
        ProtectedAttributes.reject("the project", r.title());
        ProtectedAttributes.reject("the project", r.goal());
        var created = postService.create(ownerId, new com.vikisol.arena.posts.dto.CreatePostRequest("collab", r.title().trim(), r.goal().trim(),
                r.locationText(), null, "approval", null, null, null, r.tags(), null, null, null, null, null, null, false));
        Post post = requireProject(UUID.fromString(created.id()));
        detailsRepository.save(ProjectDetails.builder().post(post).category(category).where(where).weeks(r.weeks()).build());
        if (r.roles() != null && !r.roles().isEmpty()) saveRoles(post, r.roles());
        return toView(post, ownerId);
    }

    // PR4: apply for a role with a note. The same join request as POST /projects/{id}/join.
    @Transactional
    public PostJoinRequestResponse apply(UUID userId, UUID postId, ApplicationRequest request) {
        return join(userId, postId, new JoinRequest(request.roleId(), request.note()));
    }

    @Transactional
    public ProjectView uploadCover(UUID ownerId, UUID postId, org.springframework.web.multipart.MultipartFile file) {
        Post post = requireOwnedProject(ownerId, postId);
        String name = file == null ? null : file.getOriginalFilename();
        String extension = name != null && name.contains(".") ? name.substring(name.lastIndexOf('.')).toLowerCase(java.util.Locale.ROOT) : "";
        if (!Set.of(".png", ".jpg", ".jpeg", ".webp").contains(extension)) throw new BadRequestException("A cover must be a PNG, JPG or WebP image");
        ProjectDetails details = detailsFor(post);
        var stored = fileStorageService.store(file, "project-cover", postId.toString(), "cover");
        if (details.getCoverUrl() != null) fileStorageService.delete(details.getCoverUrl());
        details.setCoverUrl(stored.url());
        detailsRepository.save(details);
        return toView(post, ownerId);
    }

    @Transactional
    public ProjectView deleteCover(UUID ownerId, UUID postId) {
        Post post = requireOwnedProject(ownerId, postId);
        detailsRepository.findByPostId(postId).ifPresent(d -> {
            if (d.getCoverUrl() != null) fileStorageService.delete(d.getCoverUrl());
            d.setCoverUrl(null);
            detailsRepository.save(d);
        });
        return toView(post, ownerId);
    }

    // PR5: the team room's Plan checklist. The team is the owner and everyone approved in.
    @Transactional(readOnly = true)
    public List<MilestoneView> milestones(UUID userId, UUID postId) {
        requireTeam(userId, requireProject(postId));
        return milestoneRepository.findByPostIdOrderByPositionAscIdAsc(postId).stream().map(CollabProjectService::milestoneView).toList();
    }

    @Transactional
    public List<MilestoneView> addMilestone(UUID userId, UUID postId, String title) {
        Post post = requireProject(postId);
        requireTeam(userId, post);
        List<ProjectMilestone> existing = milestoneRepository.findByPostIdOrderByPositionAscIdAsc(postId);
        if (existing.size() >= MAX_MILESTONES) throw new BadRequestException("A plan can have at most " + MAX_MILESTONES + " milestones");
        ProtectedAttributes.reject("the milestone", title);
        int position = existing.stream().mapToInt(ProjectMilestone::getPosition).max().orElse(-1) + 1;
        milestoneRepository.save(ProjectMilestone.builder().post(post).position(position).title(title.trim())
                .createdBy(userRepository.getReferenceById(userId)).build());
        return milestones(userId, postId);
    }

    @Transactional
    public List<MilestoneView> updateMilestone(UUID userId, UUID postId, UUID milestoneId, MilestoneUpdate update) {
        Post post = requireProject(postId);
        requireTeam(userId, post);
        ProjectMilestone m = requireMilestone(postId, milestoneId);
        if (update.title() != null) {
            if (update.title().isBlank()) throw new BadRequestException("title can't be empty");
            ProtectedAttributes.reject("the milestone", update.title());
            m.setTitle(update.title().trim());
        }
        if (update.done() != null && update.done() != m.isDone()) {
            m.setDone(update.done());
            m.setDoneAt(update.done() ? Instant.now() : null);
        }
        milestoneRepository.save(m);
        return milestones(userId, postId);
    }

    // The owner, or whoever added it.
    @Transactional
    public List<MilestoneView> deleteMilestone(UUID userId, UUID postId, UUID milestoneId) {
        Post post = requireProject(postId);
        requireTeam(userId, post);
        ProjectMilestone m = requireMilestone(postId, milestoneId);
        boolean mine = m.getCreatedBy() != null && m.getCreatedBy().getId().equals(userId);
        if (!mine && !post.getAuthorUser().getId().equals(userId)) throw new AccessDeniedException("Only the owner or whoever added it can remove a milestone");
        milestoneRepository.delete(m);
        return milestones(userId, postId);
    }

    // PR6: the owner completes the project with an outcome and names contributors from the team.
    // Each contributor sees it on their profile (GET /projects/of/{userId}).
    @Transactional
    public ProjectView complete(UUID ownerId, UUID postId, CompleteRequest request) {
        Post post = requireOwnedProject(ownerId, postId);
        if (post.getStatus() != PostStatus.OPEN && post.getStatus() != PostStatus.FULL) {
            throw new BadRequestException("This project is already " + post.getStatus().wireValue());
        }
        ProtectedAttributes.reject("the outcome", request.outcome());
        Set<UUID> team = approvedMembers(postId);
        List<UUID> contributors = request.contributorIds() == null ? List.of() : request.contributorIds().stream().distinct().toList();
        for (UUID id : contributors) {
            if (!team.contains(id)) throw new BadRequestException("Contributors must be people on the team");
        }
        ProjectDetails details = detailsFor(post);
        details.setOutcome(request.outcome().trim());
        details.setCompletedAt(Instant.now());
        detailsRepository.save(details);
        post.setStatus(PostStatus.CLOSED);
        postRepository.save(post);
        for (UUID id : contributors) {
            User user = userRepository.getReferenceById(id);
            contributorRepository.save(ProjectContributor.builder().post(post).user(user).build());
            notificationService.notifyActivity(user, "Project completed", post.getAuthorUser().getName()
                    + " completed \"" + title(post) + "\" and named you as a contributor. It's on your profile now.", post.getAuthorUser());
        }
        return toView(post, ownerId);
    }

    private Set<UUID> approvedMembers(UUID postId) {
        return joinRepository.findByPostIdAndStatusOrderByCreatedAtAscIdAsc(postId, PostJoinStatus.APPROVED)
                .stream().map(j -> j.getUser().getId()).collect(Collectors.toSet());
    }

    private void requireTeam(UUID userId, Post post) {
        if (!post.getAuthorUser().getId().equals(userId) && !approvedMembers(post.getId()).contains(userId)) {
            throw new AccessDeniedException("Only the project's team can see its plan");
        }
    }

    private ProjectMilestone requireMilestone(UUID postId, UUID milestoneId) {
        return milestoneRepository.findById(milestoneId).filter(m -> m.getPost().getId().equals(postId))
                .orElseThrow(() -> new ResourceNotFoundException("Milestone not found: " + milestoneId));
    }

    private ProjectDetails detailsFor(Post post) {
        return detailsRepository.findByPostId(post.getId()).orElseGet(() -> ProjectDetails.builder().post(post).build());
    }

    private static MilestoneView milestoneView(ProjectMilestone m) {
        return new MilestoneView(m.getId().toString(), m.getTitle(), m.isDone(), m.getDoneAt() == null ? null : m.getDoneAt().toString());
    }

    private static <E extends Enum<E>> E parse(Class<E> type, String value, String label) {
        String v = value == null ? "" : value.trim().toUpperCase(java.util.Locale.ROOT).replace('-', '_');
        for (E e : type.getEnumConstants()) {
            if (e.name().equals(v)) return e;
        }
        throw new BadRequestException(label + " must be one of " + String.join(", ",
                java.util.Arrays.stream(type.getEnumConstants()).map(e -> e.name().toLowerCase()).toList()));
    }

    private List<String> readList(String json) {
        try {
            return objectMapper.readValue(json == null ? "[]" : json, new com.fasterxml.jackson.core.type.TypeReference<List<String>>() { });
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException("Invalid stored list", e);
        }
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (com.fasterxml.jackson.core.JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
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

    // ARCHITECT-REVIEW-BE-1 blocker #2: permitAll(), used to answer for a hidden/blocked/
    // banned/deleted person too.
    @Transactional(readOnly = true)
    public Page<ProjectCard> projectsOf(UUID userId, UUID viewerId, Pageable pageable) {
        final UUID personId = asUserId(userId);
        visibilityGuard.requireVisibleTo(viewerId, personId);
        Page<Post> page = postRepository.findCollabProjectsOf(personId, pageable);
        Map<UUID, ProjectDetails> details = detailsRepository.findByPostIdIn(page.stream().map(Post::getId).toList()).stream()
                .collect(Collectors.toMap(d -> d.getPost().getId(), Function.identity()));
        Set<UUID> contributed = contributorRepository.findByUserId(personId).stream().map(c -> c.getPost().getId()).collect(Collectors.toSet());
        return page.map(p -> {
            String role = p.getAuthorUser().getId().equals(personId) ? "owner"
                    : memberRepository.findByPostIdAndUserId(p.getId(), personId).map(ProjectMember::getRole).map(ProjectRole::getTitle).orElse("member");
            ProjectDetails d = details.get(p.getId());
            return new ProjectCard(p.getId().toString(), title(p), p.getStatus().wireValue(), role, p.getCreatedAt().toString(),
                    d == null ? null : d.getOutcome(), d == null || d.getCompletedAt() == null ? null : d.getCompletedAt().toString(),
                    contributed.contains(p.getId()));
        });
    }

    // G32: the profile's Hosted / Joined / Helped / Projects row.
    // ARCHITECT-REVIEW-BE-1 blocker #2: only checked deletedAt before - not banned, blocked, or
    // hidden/nearby visibility, so a stranger could still read anyone's stats.
    @Transactional(readOnly = true)
    public ProfileStats stats(UUID userId, UUID viewerId) {
        userId = asUserId(userId);
        visibilityGuard.requireVisibleTo(viewerId, userId);
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
                members.stream().filter(m -> m.getRole() != null && m.getRole().getId().equals(r.getId()) && approved.contains(m.getUser().getId())).count(),
                readList(r.getSkillsJson()), r.getHoursPerWeek()))
                .toList();
        Viewer viewer = null;
        if (viewerId != null) {
            String joinStatus = joinRepository.findByPostIdAndUserId(post.getId(), viewerId)
                    .filter(j -> j.getStatus() != PostJoinStatus.WITHDRAWN).map(j -> j.getStatus().wireValue()).orElse(null);
            ProjectMember mine = byUser.get(viewerId);
            viewer = new Viewer(post.getAuthorUser().getId().equals(viewerId), joinStatus,
                    mine == null || mine.getRole() == null ? null : mine.getRole().getId().toString());
        }
        ProjectDetails d = detailsRepository.findByPostId(post.getId()).orElse(null);
        List<MemberView> contributors = null;
        if (d != null && d.getCompletedAt() != null) {
            List<UUID> ids = contributorRepository.findByPostId(post.getId()).stream().map(c -> c.getUser().getId()).toList();
            Map<UUID, CandidateProfile> cp = candidateProfileRepository.mapByUserId(ids);
            contributors = ids.stream().map(uid -> {
                ProjectMember m = byUser.get(uid);
                CandidateProfile p = cp.get(uid);
                return new MemberView(uid.toString(), p != null ? p.getName() : (m != null ? m.getUser().getName() : "Member"),
                        p != null ? p.getAvatarEmoji() : "🧑🏽",
                        m == null || m.getRole() == null ? null : m.getRole().getId().toString(),
                        m == null || m.getRole() == null ? null : m.getRole().getTitle());
            }).sorted(Comparator.comparing(MemberView::name)).toList();
        }
        return new ProjectView(post.getId().toString(), title(post), post.getStatus().wireValue(), roleViews, team, viewer,
                post.getBody(), d == null || d.getCategory() == null ? null : d.getCategory().name().toLowerCase(),
                d == null || d.getWhere() == null ? null : d.getWhere().name().toLowerCase(), d == null ? null : d.getWeeks(),
                d == null ? null : fileSigningService.sign(d.getCoverUrl()), d == null ? null : d.getOutcome(),
                d == null || d.getCompletedAt() == null ? null : d.getCompletedAt().toString(), contributors);
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

    /** Profile pages address a person by either their user id or their candidate-profile id. */
    private UUID asUserId(UUID id) {
        if (id == null || userRepository.existsById(id)) return id;
        return candidateProfileRepository.findById(id).map(p -> p.getUser().getId()).orElse(id);
    }
}
