package com.vikisol.arena.needs.service;

import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.follows.service.BlockService;
import com.vikisol.arena.messaging.dto.ConversationResponse;
import com.vikisol.arena.messaging.repository.ConversationRepository;
import com.vikisol.arena.messaging.service.ConversationService;
import com.vikisol.arena.needs.dto.NeedDtos.*;
import com.vikisol.arena.needs.entity.*;
import com.vikisol.arena.needs.repository.NeedCompletionRepository;
import com.vikisol.arena.needs.repository.NeedDetailsRepository;
import com.vikisol.arena.needs.repository.NeedResponseRepository;
import com.vikisol.arena.notifications.service.NotificationService;
import com.vikisol.arena.posts.entity.Post;
import com.vikisol.arena.posts.entity.PostIntentType;
import com.vikisol.arena.posts.entity.PostStatus;
import com.vikisol.arena.posts.repository.PostRepository;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
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

// Needs & offers (G14-G17): the Need page's list of offers of help, "Accept & open chat" into the
// pair's private conversation, and completion that only counts once BOTH sides confirm it.
@Service
@RequiredArgsConstructor
public class NeedService {

    private final PostRepository postRepository;
    private final NeedDetailsRepository detailsRepository;
    private final NeedResponseRepository responseRepository;
    private final NeedCompletionRepository completionRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final UserRepository userRepository;
    private final ConversationService conversationService;
    private final ConversationRepository conversationRepository;
    private final BlockService blockService;
    private final NotificationService notificationService;

    @Transactional(readOnly = true)
    public NeedView get(UUID postId, UUID viewerId) {
        return toView(requireNeed(postId), viewerId);
    }

    @Transactional
    public NeedView setDetails(UUID ownerId, UUID postId, DetailsRequest request) {
        Post post = requireOwnedNeed(ownerId, postId);
        NeedCategory category;
        try {
            category = NeedCategory.fromWire(request.category());
        } catch (IllegalArgumentException e) {
            throw new BadRequestException(e.getMessage());
        }
        NeedDetails details = detailsRepository.findByPostId(postId).orElseGet(() -> NeedDetails.builder().post(post).build());
        details.setCategory(category);
        details.setPreferredTime(request.preferredTime() == null || request.preferredTime().isBlank() ? null : request.preferredTime().trim());
        detailsRepository.save(details);
        return toView(post, ownerId);
    }

    // G15: offer help on a need, or ask for what an offer gives.
    @Transactional
    public ResponseView respond(UUID userId, UUID postId, String message) {
        Post post = requireNeed(postId);
        if (post.getAuthorUser().getId().equals(userId)) throw new BadRequestException("That's your own post");
        if (post.isAnonymous()) {
            throw new BadRequestException("This was posted anonymously, so reply in the thread instead");
        }
        if (post.getStatus() != PostStatus.OPEN) throw new BadRequestException("This is no longer open");
        if (blockService.isBlockedEitherDirection(userId, post.getAuthorUser().getId())) throw new BadRequestException("You can't respond to this");
        NeedResponse response = responseRepository.findByPostIdAndUserId(postId, userId).orElse(null);
        if (response != null && response.getStatus() != ResponseStatus.WITHDRAWN) {
            throw new BadRequestException(response.getStatus() == ResponseStatus.DECLINED
                    ? "The owner already said no to your response" : "You've already responded");
        }
        if (response == null) response = NeedResponse.builder().post(post).user(requireUser(userId)).build();
        response.setMessage(message.trim());
        response.setStatus(ResponseStatus.PENDING);
        response.setDecidedAt(null);
        response = responseRepository.save(response);
        notificationService.notifySystem(post.getAuthorUser(), post.getIntentType() == PostIntentType.ASK ? "Someone can help" : "Someone's interested",
                response.getUser().getName() + " responded to \"" + preview(post) + "\".");
        return toResponseView(response, userId, profileOf(response.getUser().getId()), null);
    }

    // The owner sees every live response with its message; anyone else sees only their own.
    @Transactional(readOnly = true)
    public List<ResponseView> responses(UUID viewerId, UUID postId) {
        Post post = requireNeed(postId);
        boolean owner = post.getAuthorUser().getId().equals(viewerId);
        List<NeedResponse> rows = owner
                ? responseRepository.findByPostIdAndStatusNotOrderByCreatedAtAscIdAsc(postId, ResponseStatus.WITHDRAWN)
                : responseRepository.findByPostIdAndUserId(postId, viewerId).filter(r -> r.getStatus() != ResponseStatus.WITHDRAWN).stream().toList();
        Map<UUID, CandidateProfile> profiles = candidateProfileRepository.mapByUserId(rows.stream().map(r -> r.getUser().getId()).toList());
        Map<UUID, NeedCompletion> completions = completionRepository.findByResponseIdIn(rows.stream().map(NeedResponse::getId).toList())
                .stream().collect(Collectors.toMap(c -> c.getResponse().getId(), Function.identity()));
        return rows.stream().map(r -> toResponseView(r, viewerId, profiles.get(r.getUser().getId()), completions.get(r.getId()))).toList();
    }

    // G15: accept -> the pair's private conversation (the existing one-per-pair DM).
    @Transactional
    public ResponseView accept(UUID ownerId, UUID postId, UUID responseId) {
        Post post = requireOwnedNeed(ownerId, postId);
        NeedResponse response = requireResponse(postId, responseId);
        if (response.getStatus() != ResponseStatus.PENDING) throw new BadRequestException("This response has already been answered");
        if (blockService.isBlockedEitherDirection(ownerId, response.getUser().getId())) throw new BadRequestException("You can't accept this response");
        ConversationResponse chat = conversationService.getOrCreate(ownerId, response.getUser().getId(), "About: " + preview(post));
        response.setConversation(conversationRepository.getReferenceById(UUID.fromString(chat.id())));
        response.setStatus(ResponseStatus.ACCEPTED);
        response.setDecidedAt(Instant.now());
        responseRepository.save(response);
        notificationService.notifySystem(response.getUser(), "Accepted",
                post.getAuthorUser().getName() + " accepted your response. You can chat now.");
        return toResponseView(response, ownerId, profileOf(response.getUser().getId()), null);
    }

    @Transactional
    public ResponseView decline(UUID ownerId, UUID postId, UUID responseId) {
        requireOwnedNeed(ownerId, postId);
        NeedResponse response = requireResponse(postId, responseId);
        if (response.getStatus() != ResponseStatus.PENDING) throw new BadRequestException("This response has already been answered");
        response.setStatus(ResponseStatus.DECLINED);
        response.setDecidedAt(Instant.now());
        responseRepository.save(response);
        return toResponseView(response, ownerId, profileOf(response.getUser().getId()), null);
    }

    @Transactional
    public ResponseView withdraw(UUID userId, UUID postId) {
        requireNeed(postId);
        NeedResponse response = responseRepository.findByPostIdAndUserId(postId, userId)
                .filter(r -> r.getStatus() == ResponseStatus.PENDING || r.getStatus() == ResponseStatus.ACCEPTED)
                .orElseThrow(() -> new BadRequestException("There's nothing to withdraw"));
        NeedCompletion completion = completionRepository.findByResponseId(response.getId()).orElse(null);
        if (completion != null && completion.getCompletedAt() != null) throw new BadRequestException("This is already completed");
        response.setStatus(ResponseStatus.WITHDRAWN);
        responseRepository.save(response);
        return toResponseView(response, userId, profileOf(userId), completion);
    }

    // G16: each side confirms; it's an outcome only when both have.
    @Transactional
    public ResponseView confirm(UUID userId, UUID postId, UUID responseId, String note) {
        Post post = requireNeed(postId);
        NeedResponse response = requireResponse(postId, responseId);
        boolean owner = post.getAuthorUser().getId().equals(userId);
        boolean responder = response.getUser().getId().equals(userId);
        if (!owner && !responder) throw new AccessDeniedException("Only the two people involved can confirm this");
        if (response.getStatus() != ResponseStatus.ACCEPTED) throw new BadRequestException("Accept the response before marking it done");
        NeedCompletion completion = completionRepository.findByResponseId(responseId)
                .orElseGet(() -> NeedCompletion.builder().response(response).build());
        if (completion.getCompletedAt() != null) throw new BadRequestException("This is already completed");
        String cleanNote = note == null || note.isBlank() ? null : note.trim();
        Instant now = Instant.now();
        if (owner) {
            completion.setOwnerConfirmedAt(now);
            completion.setOwnerNote(cleanNote);
        } else {
            completion.setResponderConfirmedAt(now);
            completion.setResponderNote(cleanNote);
        }
        User other = owner ? response.getUser() : post.getAuthorUser();
        if (completion.getOwnerConfirmedAt() != null && completion.getResponderConfirmedAt() != null) {
            completion.setCompletedAt(now);
            if (post.getIntentType() == PostIntentType.ASK && post.getStatus() == PostStatus.OPEN) {
                post.setStatus(PostStatus.CLOSED);
                postRepository.save(post);
            }
            notificationService.notifySystem(other, "It's done", "You both confirmed \"" + preview(post) + "\".");
        } else {
            notificationService.notifySystem(other, "Confirm on your side",
                    "Did \"" + preview(post) + "\" happen? Confirm it so it counts for both of you.");
        }
        completionRepository.save(completion);
        return toResponseView(response, userId, profileOf(response.getUser().getId()), completion);
    }

    // G17: confirmed outcomes on a public profile. Only what and when - never the other person.
    @Transactional(readOnly = true)
    public Page<OutcomeView> outcomes(UUID userId, Pageable pageable) {
        Page<NeedCompletion> page = completionRepository.findCompletedFor(userId, pageable);
        Map<UUID, NeedDetails> details = detailsRepository.findByPostIdIn(page.stream().map(c -> c.getResponse().getPost().getId()).toList())
                .stream().collect(Collectors.toMap(d -> d.getPost().getId(), Function.identity()));
        return page.map(c -> {
            Post post = c.getResponse().getPost();
            boolean isOwner = post.getAuthorUser().getId().equals(userId);
            boolean ask = post.getIntentType() == PostIntentType.ASK;
            // On a need the responder gave help; on an offer the owner gave it.
            boolean gave = ask != isOwner;
            NeedDetails d = details.get(post.getId());
            return new OutcomeView(post.getId().toString(), ask ? "need" : "offer",
                    d == null ? null : d.getCategory().wireValue(), gave ? "gave" : "received", c.getCompletedAt().toString());
        });
    }

    // "My offers" on Work: what I responded to.
    @Transactional(readOnly = true)
    public Page<MyResponseView> myResponses(UUID userId, Pageable pageable) {
        Page<NeedResponse> page = responseRepository.findByUserIdOrderByCreatedAtDescIdDesc(userId, pageable);
        Map<UUID, NeedCompletion> completions = completionRepository.findByResponseIdIn(page.stream().map(NeedResponse::getId).toList())
                .stream().collect(Collectors.toMap(c -> c.getResponse().getId(), Function.identity()));
        CandidateProfile me = profileOf(userId);
        return page.map(r -> new MyResponseView(r.getPost().getId().toString(), preview(r.getPost()),
                r.getPost().getIntentType() == PostIntentType.ASK ? "need" : "offer",
                toResponseView(r, userId, me, completions.get(r.getId()))));
    }

    // --- helpers ---

    private NeedView toView(Post post, UUID viewerId) {
        NeedDetails details = detailsRepository.findByPostId(post.getId()).orElse(null);
        long count = responseRepository.countByPostIdAndStatusNot(post.getId(), ResponseStatus.WITHDRAWN);
        Viewer viewer = null;
        if (viewerId != null) {
            ResponseView mine = responseRepository.findByPostIdAndUserId(post.getId(), viewerId)
                    .filter(r -> r.getStatus() != ResponseStatus.WITHDRAWN)
                    .map(r -> toResponseView(r, viewerId, profileOf(viewerId), completionRepository.findByResponseId(r.getId()).orElse(null)))
                    .orElse(null);
            viewer = new Viewer(post.getAuthorUser().getId().equals(viewerId), mine);
        }
        return new NeedView(post.getId().toString(), post.getIntentType() == PostIntentType.ASK ? "need" : "offer",
                details == null ? null : details.getCategory().wireValue(), details == null ? null : details.getPreferredTime(),
                post.getStatus().wireValue(), count, viewer);
    }

    // Private parts (chat, completion) only for the owner and the responder.
    private ResponseView toResponseView(NeedResponse r, UUID viewerId, CandidateProfile profile, NeedCompletion completion) {
        boolean involved = r.getUser().getId().equals(viewerId) || r.getPost().getAuthorUser().getId().equals(viewerId);
        CompletionView completionView = !involved || completion == null ? null : new CompletionView(
                str(completion.getOwnerConfirmedAt()), str(completion.getResponderConfirmedAt()),
                completion.getOwnerNote(), completion.getResponderNote(), str(completion.getCompletedAt()));
        return new ResponseView(r.getId().toString(), r.getPost().getId().toString(), r.getUser().getId().toString(),
                profile != null ? profile.getName() : r.getUser().getName(), profile != null ? profile.getAvatarEmoji() : "🧑🏽",
                r.getMessage(), r.getStatus().wireValue(), r.getCreatedAt().toString(),
                involved && r.getConversation() != null ? r.getConversation().getId().toString() : null, completionView);
    }

    private CandidateProfile profileOf(UUID userId) {
        return candidateProfileRepository.findByUserId(userId).orElse(null);
    }

    private static String str(Instant i) {
        return i == null ? null : i.toString();
    }

    private static String preview(Post post) {
        String text = post.getTitle() != null ? post.getTitle() : post.getBody();
        return text.length() > 60 ? text.substring(0, 60) + "…" : text;
    }

    private NeedResponse requireResponse(UUID postId, UUID responseId) {
        return responseRepository.findByIdAndPostId(responseId, postId)
                .orElseThrow(() -> new ResourceNotFoundException("Response not found: " + responseId));
    }

    private Post requireNeed(UUID postId) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new ResourceNotFoundException("Need not found: " + postId));
        if ((post.getIntentType() != PostIntentType.ASK && post.getIntentType() != PostIntentType.OFFER) || post.getRemovedReason() != null) {
            throw new ResourceNotFoundException("Need not found: " + postId);
        }
        return post;
    }

    private Post requireOwnedNeed(UUID ownerId, UUID postId) {
        Post post = requireNeed(postId);
        if (!post.getAuthorUser().getId().equals(ownerId)) throw new AccessDeniedException("Not your post");
        return post;
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }
}
