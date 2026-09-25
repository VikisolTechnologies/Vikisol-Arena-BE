package com.vikisol.arena.communities.service;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.communities.dto.*;
import com.vikisol.arena.communities.entity.Community;
import com.vikisol.arena.communities.entity.CommunityMember;
import com.vikisol.arena.communities.entity.CommunityRole;
import com.vikisol.arena.communities.repository.CommunityMemberRepository;
import com.vikisol.arena.communities.repository.CommunityRepository;
import com.vikisol.arena.posts.dto.PostResponse;
import com.vikisol.arena.posts.entity.Post;
import com.vikisol.arena.posts.entity.PostStatus;
import com.vikisol.arena.posts.repository.PostRepository;
import com.vikisol.arena.posts.service.PostService;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import com.vikisol.arena.search.SearchText;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.text.Normalizer;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Arena restructure Phase 2 (Discuss): user-created communities. Anyone can start one and becomes
 * its OWNER; the owner can make members MODERATORs; owners and moderators can remove posts and
 * ban members. Posting a discussion into a community joins you automatically (see PostService).
 */
@Service
@RequiredArgsConstructor
public class CommunityService {

    // Enough for a real person, low enough that nobody can squat hundreds of names.
    static final int MAX_OWNED = 10;
    private static final List<PostStatus> LIVE = List.of(PostStatus.OPEN, PostStatus.FULL);

    private final CommunityRepository communityRepository;
    private final CommunityMemberRepository memberRepository;
    private final PostRepository postRepository;
    private final PostService postService;
    private final UserRepository userRepository;
    private final CandidateProfileRepository candidateProfileRepository;

    @Transactional(readOnly = true)
    public List<CommunityResponse> list(String query, UUID viewerId) {
        List<Community> all = communityRepository.findAll();
        List<String> terms = SearchText.terms(query);
        if (!terms.isEmpty()) {
            all = all.stream()
                    .filter(c -> SearchText.score(terms, c.getName(), SearchText.haystack(c.getSlug(), c.getDescription())) > 0)
                    .toList();
        }
        List<CommunityResponse> out = toResponses(all, viewerId);
        return out.stream()
                .sorted(Comparator.comparingLong(CommunityResponse::memberCount).reversed()
                        .thenComparing(CommunityResponse::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public List<CommunityResponse> mine(UUID viewerId) {
        List<Community> joined = memberRepository.findByUserIdAndBannedFalse(viewerId).stream().map(CommunityMember::getCommunity).toList();
        return toResponses(joined, viewerId).stream()
                .sorted(Comparator.comparing(CommunityResponse::name, String.CASE_INSENSITIVE_ORDER))
                .toList();
    }

    @Transactional(readOnly = true)
    public CommunityResponse get(String slug, UUID viewerId) {
        return toResponses(List.of(require(slug)), viewerId).get(0);
    }

    @Transactional(readOnly = true)
    public List<PostResponse> posts(String slug, String sort, int page, int size, UUID viewerId) {
        return postService.listDiscussions(viewerId, require(slug).getId(), sort, page, size);
    }

    @Transactional(readOnly = true)
    public List<CommunityMemberResponse> moderators(String slug) {
        Community community = require(slug);
        List<CommunityMember> mods = memberRepository.findByCommunityIdAndRoleIn(community.getId(), List.of(CommunityRole.OWNER, CommunityRole.MODERATOR));
        Map<UUID, CandidateProfile> profiles = profilesFor(mods.stream().map(m -> m.getUser().getId()).toList());
        return mods.stream()
                .sorted(Comparator.comparing((CommunityMember m) -> m.getRole() != CommunityRole.OWNER))
                .map(m -> toMemberResponse(m, profiles))
                .toList();
    }

    @Transactional
    public CommunityResponse create(UUID userId, CreateCommunityRequest request) {
        User user = requireUser(userId);
        if (communityRepository.countByCreatedById(userId) >= MAX_OWNED) {
            throw new BadRequestException("You can start up to " + MAX_OWNED + " communities.");
        }
        String name = request.name().trim().replaceAll("\\s+", " ");
        String slug = slugify(name);
        // A name in Telugu, Hindi etc. has no Latin letters to make a web address from - it
        // still gets one, just a generated one ("c-4f9a2b").
        if (slug.length() < 3) {
            do {
                slug = "c-" + UUID.randomUUID().toString().substring(0, 6);
            } while (communityRepository.existsBySlug(slug));
        } else if (communityRepository.existsBySlug(slug)) {
            throw new BadRequestException("There's already a community called that - join it, or pick another name.");
        }
        Community community = communityRepository.save(Community.builder()
                .slug(slug)
                .name(name)
                .description(blankToNull(request.description()))
                .emoji(request.emoji() == null || request.emoji().isBlank() ? "💬" : request.emoji().trim())
                .createdBy(user)
                .allowAnonymous(request.allowAnonymous() == null || request.allowAnonymous())
                .build());
        memberRepository.save(CommunityMember.builder().community(community).user(user).role(CommunityRole.OWNER).build());
        return get(slug, userId);
    }

    @Transactional
    public CommunityResponse update(String slug, UUID userId, UpdateCommunityRequest request) {
        Community community = require(slug);
        if (roleOf(community, userId) != CommunityRole.OWNER) {
            throw new AccessDeniedException("Only the community's owner can change it");
        }
        if (request.description() != null) community.setDescription(blankToNull(request.description()));
        if (request.emoji() != null && !request.emoji().isBlank()) community.setEmoji(request.emoji().trim());
        if (request.allowAnonymous() != null) community.setAllowAnonymous(request.allowAnonymous());
        return get(slug, userId);
    }

    @Transactional
    public CommunityResponse join(String slug, UUID userId) {
        Community community = require(slug);
        Optional<CommunityMember> existing = memberRepository.findByCommunityIdAndUserId(community.getId(), userId);
        if (existing.isPresent()) {
            if (existing.get().isBanned()) throw new BadRequestException("You've been removed from this community.");
            return get(slug, userId);
        }
        memberRepository.save(CommunityMember.builder().community(community).user(requireUser(userId)).role(CommunityRole.MEMBER).build());
        return get(slug, userId);
    }

    @Transactional
    public CommunityResponse leave(String slug, UUID userId) {
        Community community = require(slug);
        memberRepository.findByCommunityIdAndUserId(community.getId(), userId).ifPresent(m -> {
            if (m.getRole() == CommunityRole.OWNER) {
                throw new BadRequestException("You own this community, so you can't leave it.");
            }
            // A ban outlives leaving - otherwise leave + rejoin would lift it.
            if (!m.isBanned()) memberRepository.delete(m);
        });
        return get(slug, userId);
    }

    /** Owner only: make a member a moderator (value=true) or back to a member (false). */
    @Transactional
    public CommunityMemberResponse setModerator(String slug, UUID ownerId, UUID targetUserId, boolean moderator) {
        Community community = require(slug);
        if (roleOf(community, ownerId) != CommunityRole.OWNER) {
            throw new AccessDeniedException("Only the owner can choose moderators");
        }
        CommunityMember target = requireMember(community, targetUserId);
        if (target.getRole() == CommunityRole.OWNER) throw new BadRequestException("The owner is already in charge.");
        if (target.isBanned()) throw new BadRequestException("Unban them first.");
        target.setRole(moderator ? CommunityRole.MODERATOR : CommunityRole.MEMBER);
        return toMemberResponse(target, profilesFor(List.of(targetUserId)));
    }

    /** Owner/moderator: ban (value=true) or unban a member. Moderators can't ban the owner or each other. */
    @Transactional
    public CommunityMemberResponse ban(String slug, UUID modId, UUID targetUserId, boolean banned) {
        Community community = require(slug);
        CommunityRole actor = roleOf(community, modId);
        if (actor == null || !actor.canModerate()) throw new AccessDeniedException("Only moderators can ban members");
        if (targetUserId.equals(modId)) throw new BadRequestException("You can't ban yourself.");
        CommunityMember target = memberRepository.findByCommunityIdAndUserId(community.getId(), targetUserId)
                // Banning someone who never joined still has to stick - record them as a banned member.
                .orElseGet(() -> CommunityMember.builder().community(community).user(requireUser(targetUserId)).role(CommunityRole.MEMBER).build());
        if (target.getRole() == CommunityRole.OWNER) throw new BadRequestException("The owner can't be banned.");
        if (target.getRole() == CommunityRole.MODERATOR && actor != CommunityRole.OWNER) {
            throw new AccessDeniedException("Only the owner can ban a moderator");
        }
        target.setBanned(banned);
        if (banned) target.setRole(CommunityRole.MEMBER);
        memberRepository.save(target);
        return toMemberResponse(target, profilesFor(List.of(targetUserId)));
    }

    /** Owner/moderator: take a post down from their community. It stays for the author's records as CLOSED. */
    @Transactional
    public void removePost(String slug, UUID modId, UUID postId, String reason) {
        Community community = require(slug);
        CommunityRole actor = roleOf(community, modId);
        if (actor == null || !actor.canModerate()) throw new AccessDeniedException("Only moderators can remove posts");
        Post post = postRepository.findById(postId).orElseThrow(() -> new ResourceNotFoundException("Post not found"));
        if (post.getCommunity() == null || !post.getCommunity().getId().equals(community.getId())) {
            throw new BadRequestException("That post isn't in this community.");
        }
        post.setStatus(PostStatus.CLOSED);
        post.setRemovedReason(reason == null || reason.isBlank() ? "Removed by a moderator" : reason.trim());
    }

    // --- helpers ---

    private List<CommunityResponse> toResponses(List<Community> communities, UUID viewerId) {
        if (communities.isEmpty()) return List.of();
        Set<UUID> ids = communities.stream().map(Community::getId).collect(Collectors.toSet());
        Map<UUID, Long> members = memberRepository.countMembers(ids).stream()
                .collect(Collectors.toMap(CommunityMemberRepository.CommunityCount::getCommunityId, CommunityMemberRepository.CommunityCount::getCnt));
        Map<UUID, Long> posts = postRepository.countByCommunity(ids, LIVE).stream()
                .collect(Collectors.toMap(PostRepository.CommunityPostCount::getCommunityId, PostRepository.CommunityPostCount::getCnt));
        Map<UUID, CommunityMember> mine = viewerId == null ? Map.of()
                : memberRepository.findMine(viewerId, ids).stream().collect(Collectors.toMap(m -> m.getCommunity().getId(), Function.identity()));
        return communities.stream().map(c -> {
            CommunityMember m = mine.get(c.getId());
            return new CommunityResponse(c.getId().toString(), c.getSlug(), c.getName(), c.getDescription(), c.getEmoji(),
                    members.getOrDefault(c.getId(), 0L), posts.getOrDefault(c.getId(), 0L), c.isAllowAnonymous(),
                    m == null || m.isBanned() ? null : m.getRole().wireValue(), m != null && m.isBanned(),
                    c.getCreatedAt().toString(), c.isDemoContent());
        }).toList();
    }

    private CommunityMemberResponse toMemberResponse(CommunityMember m, Map<UUID, CandidateProfile> profiles) {
        CandidateProfile p = profiles.get(m.getUser().getId());
        return new CommunityMemberResponse(m.getUser().getId().toString(),
                p != null ? p.getName() : m.getUser().getName(), p != null ? p.getAvatarEmoji() : "🧑🏽",
                m.getRole().wireValue(), m.isBanned());
    }

    private Map<UUID, CandidateProfile> profilesFor(List<UUID> userIds) {
        if (userIds.isEmpty()) return Map.of();
        return candidateProfileRepository.findByUserIdIn(userIds).stream()
                .collect(Collectors.toMap(p -> p.getUser().getId(), Function.identity(), (a, b) -> a));
    }

    private CommunityRole roleOf(Community community, UUID userId) {
        if (userId == null) return null;
        return memberRepository.findByCommunityIdAndUserId(community.getId(), userId)
                .filter(m -> !m.isBanned())
                .map(CommunityMember::getRole)
                .orElse(null);
    }

    private CommunityMember requireMember(Community community, UUID userId) {
        return memberRepository.findByCommunityIdAndUserId(community.getId(), userId)
                .orElseThrow(() -> new BadRequestException("They aren't a member of this community."));
    }

    private Community require(String slug) {
        return communityRepository.findBySlug(slug == null ? "" : slug.toLowerCase(Locale.ROOT))
                .orElseThrow(() -> new ResourceNotFoundException("Community not found"));
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }

    private static String blankToNull(String s) {
        return s == null || s.isBlank() ? null : s.trim();
    }

    /** "Hyderabad Foodies & Cafés!" -> "hyderabad-foodies-cafes". */
    static String slugify(String name) {
        String ascii = Normalizer.normalize(name, Normalizer.Form.NFD).replaceAll("\\p{M}", "");
        String slug = ascii.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "-").replaceAll("^-+|-+$", "");
        return slug.length() > 40 ? slug.substring(0, 40).replaceAll("-+$", "") : slug;
    }
}
