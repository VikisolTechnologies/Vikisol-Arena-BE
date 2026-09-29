package com.vikisol.arena.activities.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vikisol.arena.activities.ActivityRules;
import com.vikisol.arena.activities.dto.ActivityDtos.*;
import com.vikisol.arena.activities.entity.*;
import com.vikisol.arena.activities.repository.*;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.common.policy.ProtectedAttributes;
import com.vikisol.arena.common.service.FileSigningService;
import com.vikisol.arena.common.service.FileStorageService;
import com.vikisol.arena.common.util.AgeUtil;
import com.vikisol.arena.follows.service.BlockService;
import com.vikisol.arena.notifications.service.NotificationService;
import com.vikisol.arena.posts.dto.PostJoinRequestResponse;
import com.vikisol.arena.posts.entity.*;
import com.vikisol.arena.posts.repository.PostJoinRequestRepository;
import com.vikisol.arena.posts.repository.PostRepository;
import com.vikisol.arena.posts.service.PostService;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.time.Instant;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

// Everything an ACTIVITY post has beyond the post itself (G7-G13). Joining still goes through
// PostService (the Jenny-facing join path), so capacity, rooms and notifications stay in one
// place; this service adds the answers, the queue, check-in, disputes and private feedback.
@Service
@RequiredArgsConstructor
public class ActivitiesService {

    private static final int MAX_DETAIL_KEYS = 12;
    private static final int MAX_DETAIL_VALUE = 200;
    private static final Set<String> COVER_EXTENSIONS = Set.of(".png", ".jpg", ".jpeg", ".webp");

    private final PostRepository postRepository;
    private final PostJoinRequestRepository joinRepository;
    private final PostService postService;
    private final ActivityDetailsRepository detailsRepository;
    private final ActivityQuestionRepository questionRepository;
    private final ActivityAnswerRepository answerRepository;
    private final ActivityWaitlistRepository waitlistRepository;
    private final ActivityAttendanceRepository attendanceRepository;
    private final ActivityFeedbackRepository feedbackRepository;
    private final UserRepository userRepository;
    private final CandidateProfileRepository candidateProfileRepository;
    private final BlockService blockService;
    private final NotificationService notificationService;
    private final FileStorageService fileStorageService;
    private final FileSigningService fileSigningService;
    private final ObjectMapper objectMapper;

    // --- G7/G13: details and cover ---------------------------------------------------------

    @Transactional(readOnly = true)
    public ActivityResponse get(UUID postId, UUID viewerId) {
        Post post = requireActivity(postId);
        return toResponse(post, viewerId);
    }

    @Transactional
    public ActivityResponse updateDetails(UUID hostId, UUID postId, UpdateDetailsRequest request) {
        Post post = requireHostedActivity(hostId, postId);
        ActivityDetails details = detailsRepository.findByPostId(postId).orElse(null);
        if (details == null) {
            if (request.kind() == null) throw new BadRequestException("kind is required the first time");
            details = ActivityDetails.builder().post(post).build();
        }
        if (request.kind() != null) {
            try {
                details.setKind(ActivityKind.fromWire(request.kind()));
            } catch (IllegalArgumentException e) {
                throw new BadRequestException(e.getMessage());
            }
        }
        if (request.details() != null) details.setDetailsJson(writeDetails(cleanDetails(details.getKind(), request.details())));
        else details.setDetailsJson(writeDetails(cleanDetails(details.getKind(), readDetails(details))));
        if (request.waitlistEnabled() != null) details.setWaitlistEnabled(request.waitlistEnabled());
        detailsRepository.save(details);
        return toResponse(post, hostId);
    }

    @Transactional
    public ActivityResponse uploadCover(UUID hostId, UUID postId, MultipartFile file) {
        Post post = requireHostedActivity(hostId, postId);
        String name = file == null ? null : file.getOriginalFilename();
        String extension = name != null && name.contains(".") ? name.substring(name.lastIndexOf('.')).toLowerCase(Locale.ROOT) : "";
        if (!COVER_EXTENSIONS.contains(extension)) throw new BadRequestException("A cover must be a PNG, JPG or WebP image");
        ActivityDetails details = requireDetails(post);
        FileStorageService.StoredFile stored = fileStorageService.store(file, "activity-cover", postId.toString(), "cover");
        if (details.getCoverUrl() != null) fileStorageService.delete(details.getCoverUrl());
        details.setCoverUrl(stored.url());
        detailsRepository.save(details);
        return toResponse(post, hostId);
    }

    @Transactional
    public ActivityResponse deleteCover(UUID hostId, UUID postId) {
        Post post = requireHostedActivity(hostId, postId);
        detailsRepository.findByPostId(postId).ifPresent(details -> {
            if (details.getCoverUrl() != null) fileStorageService.delete(details.getCoverUrl());
            details.setCoverUrl(null);
            detailsRepository.save(details);
        });
        return toResponse(post, hostId);
    }

    // --- G8: host questions -------------------------------------------------------------------

    @Transactional
    public ActivityResponse setQuestions(UUID hostId, UUID postId, List<QuestionInput> questions) {
        Post post = requireHostedActivity(hostId, postId);
        if (questions.size() > ActivityRules.MAX_QUESTIONS) {
            throw new BadRequestException("You can ask at most " + ActivityRules.MAX_QUESTIONS + " questions");
        }
        if (answerRepository.existsByQuestionPostId(postId)) {
            throw new BadRequestException("People have already answered these questions, so they can't change now");
        }
        questions.forEach(q -> ProtectedAttributes.reject("the question", q.text()));
        questionRepository.deleteByPostId(postId);
        questionRepository.flush();
        int position = 0;
        for (QuestionInput q : questions) {
            questionRepository.save(ActivityQuestion.builder().post(post).position(position++)
                    .text(q.text().trim()).required(!Boolean.FALSE.equals(q.required())).build());
        }
        return toResponse(post, hostId);
    }

    // Join with answers. The request itself (capacity, approval, room) is PostService's.
    @Transactional
    public PostJoinRequestResponse join(UUID userId, UUID postId, List<AnswerInput> answers) {
        Post post = requireActivity(postId);
        saveAnswers(post, requireUser(userId), answers);
        return postService.requestJoin(userId, postId, true);
    }

    // Host, or the person who answered.
    @Transactional(readOnly = true)
    public List<AnswerResponse> answers(UUID viewerId, UUID postId, UUID userId) {
        Post post = requireActivity(postId);
        if (!isHost(post, viewerId) && !userId.equals(viewerId)) throw new AccessDeniedException("Not your activity");
        return answerRepository.findForPostAndUser(postId, userId).stream()
                .map(a -> new AnswerResponse(a.getQuestion().getId().toString(), a.getQuestion().getText(), a.getAnswer()))
                .toList();
    }

    // --- G9: waitlist ------------------------------------------------------------------------

    @Transactional
    public ActivityResponse joinWaitlist(UUID userId, UUID postId, List<AnswerInput> answers) {
        Post post = postRepository.findByIdForUpdate(postId).orElseThrow(() -> notFound(postId));
        requireActivity(post);
        if (isHost(post, userId)) throw new BadRequestException("You're hosting this activity");
        ActivityDetails details = detailsRepository.findByPostId(postId).orElse(null);
        if (details != null && !details.isWaitlistEnabled()) throw new BadRequestException("This activity doesn't have a waitlist");
        Instant now = Instant.now();
        if (post.getStartsAt() != null && !post.getStartsAt().isAfter(now)) throw new BadRequestException("This activity has already started");
        boolean full = post.getStatus() == PostStatus.FULL
                || (post.getStatus() == PostStatus.OPEN && post.getCapacity() != null && post.getSpotsFilled() >= post.getCapacity());
        if (!full) {
            if (post.getStatus() == PostStatus.OPEN) throw new BadRequestException("There are still spots. Request to join instead.");
            throw new BadRequestException("This activity is no longer open");
        }
        joinRepository.findByPostIdAndUserId(postId, userId)
                .filter(j -> j.getStatus() == PostJoinStatus.APPROVED || j.getStatus() == PostJoinStatus.PENDING)
                .ifPresent(j -> {
                    throw new BadRequestException("You've already asked to join this activity");
                });
        User user = requireUser(userId);
        if (blockService.isBlockedEitherDirection(userId, post.getAuthorUser().getId())) throw new BadRequestException("You can't join this activity");
        if (user.getDateOfBirth() == null || !AgeUtil.isAdult(user.getDateOfBirth())) {
            throw new BadRequestException("You must be " + AgeUtil.MINIMUM_AGE + " or older to join an activity");
        }
        ActivityWaitlistEntry entry = waitlistRepository.findByPostIdAndUserId(postId, userId).orElse(null);
        if (entry != null && entry.getStatus() == WaitlistStatus.WAITING) throw new BadRequestException("You're already on the waitlist");
        saveAnswers(post, user, answers);
        if (entry == null) entry = ActivityWaitlistEntry.builder().post(post).user(user).build();
        entry.setStatus(WaitlistStatus.WAITING);
        entry.setJoinedAt(now);
        entry.setPromotedAt(null);
        waitlistRepository.save(entry);
        return toResponse(post, userId);
    }

    @Transactional
    public ActivityResponse leaveWaitlist(UUID userId, UUID postId) {
        Post post = requireActivity(postId);
        ActivityWaitlistEntry entry = waitlistRepository.findByPostIdAndUserId(postId, userId)
                .filter(w -> w.getStatus() == WaitlistStatus.WAITING)
                .orElseThrow(() -> new BadRequestException("You're not on the waitlist"));
        entry.setStatus(WaitlistStatus.LEFT);
        waitlistRepository.save(entry);
        return toResponse(post, userId);
    }

    @Transactional(readOnly = true)
    public List<WaitlistEntryResponse> waitlist(UUID hostId, UUID postId) {
        requireHostedActivity(hostId, postId);
        List<ActivityWaitlistEntry> queue = waitlistRepository.findByPostIdAndStatusOrderByJoinedAtAscIdAsc(postId, WaitlistStatus.WAITING);
        Map<UUID, CandidateProfile> profiles = candidateProfileRepository.mapByUserId(queue.stream().map(w -> w.getUser().getId()).toList());
        List<WaitlistEntryResponse> out = new ArrayList<>();
        for (int i = 0; i < queue.size(); i++) {
            User u = queue.get(i).getUser();
            CandidateProfile p = profiles.get(u.getId());
            out.add(new WaitlistEntryResponse(u.getId().toString(), p != null ? p.getName() : u.getName(),
                    p != null ? p.getAvatarEmoji() : "🧑🏽", i + 1, queue.get(i).getJoinedAt().toString()));
        }
        return out;
    }

    // --- G10: check-in -----------------------------------------------------------------------

    @Transactional
    public ActivityResponse checkIn(UUID userId, UUID postId) {
        Post post = requireActivity(postId);
        PostJoinRequest join = joinRepository.findByPostIdAndUserId(postId, userId)
                .filter(j -> j.getStatus() == PostJoinStatus.APPROVED)
                .orElseThrow(() -> new BadRequestException("Only people who joined can check in"));
        if (post.getStartsAt() == null) throw new BadRequestException("This activity has no start time to check in to");
        Instant now = Instant.now();
        Instant opens = post.getStartsAt().minus(ActivityRules.CHECK_IN_OPENS_BEFORE);
        Instant closes = post.getEndsAt() != null ? post.getEndsAt() : post.getStartsAt().plus(ActivityRules.CHECK_IN_DEFAULT_LENGTH);
        if (now.isBefore(opens)) throw new BadRequestException("Check-in opens an hour before the start");
        if (now.isAfter(closes)) throw new BadRequestException("Check-in has closed for this activity");
        ActivityAttendance attendance = attendanceFor(join);
        if (attendance.getCheckedInAt() == null) {
            attendance.setCheckedInAt(now);
            attendanceRepository.save(attendance);
        }
        return toResponse(post, userId);
    }

    // --- G11: attendance and the 72h dispute --------------------------------------------------

    // Host-only. Attendance is private: nobody else sees who was marked present or absent.
    @Transactional(readOnly = true)
    public List<AttendanceRow> attendance(UUID hostId, UUID postId) {
        requireHostedActivity(hostId, postId);
        List<PostJoinRequest> joined = joinRepository.findByPostIdAndStatusOrderByCreatedAtAscIdAsc(postId, PostJoinStatus.APPROVED);
        Map<UUID, ActivityAttendance> byJoin = attendanceRepository.findByJoinRequestIdIn(joined.stream().map(PostJoinRequest::getId).toList())
                .stream().collect(Collectors.toMap(a -> a.getJoinRequest().getId(), Function.identity()));
        Map<UUID, CandidateProfile> profiles = candidateProfileRepository.mapByUserId(joined.stream().map(j -> j.getUser().getId()).toList());
        return joined.stream().map(j -> {
            ActivityAttendance a = byJoin.get(j.getId());
            CandidateProfile p = profiles.get(j.getUser().getId());
            return new AttendanceRow(j.getId().toString(), j.getUser().getId().toString(),
                    p != null ? p.getName() : j.getUser().getName(),
                    a == null || a.getCheckedInAt() == null ? null : a.getCheckedInAt().toString(),
                    j.getOutcome() == null ? null : j.getOutcome().wireValue(),
                    a == null || a.getOutcomeRecordedAt() == null ? null : a.getOutcomeRecordedAt().toString(),
                    a == null ? DisputeStatus.NONE.wireValue() : a.getDisputeStatus().wireValue(),
                    a == null ? null : a.getDisputeReason());
        }).toList();
    }

    @Transactional
    public ActivityResponse dispute(UUID userId, UUID postId, String reason) {
        Post post = requireActivity(postId);
        PostJoinRequest join = joinRepository.findByPostIdAndUserId(postId, userId)
                .filter(j -> j.getStatus() == PostJoinStatus.APPROVED)
                .orElseThrow(() -> new BadRequestException("Only people who joined can dispute attendance"));
        if (join.getOutcome() != PostJoinOutcome.NO_SHOW) throw new BadRequestException("You weren't marked as a no-show");
        ActivityAttendance attendance = attendanceFor(join);
        if (attendance.getDisputeStatus() != DisputeStatus.NONE) throw new BadRequestException("You've already disputed this");
        if (attendance.getOutcomeRecordedAt() == null
                || Instant.now().isAfter(attendance.getOutcomeRecordedAt().plus(ActivityRules.DISPUTE_WINDOW))) {
            throw new BadRequestException("The 72 hours to dispute this have passed");
        }
        attendance.setDisputeStatus(DisputeStatus.OPEN);
        attendance.setDisputeReason(reason.trim());
        attendance.setDisputedAt(Instant.now());
        attendanceRepository.save(attendance);
        notificationService.notifySystem(post.getAuthorUser(), "Attendance disputed",
                join.getUser().getName() + " says they were there. Review it on the attendance sheet.");
        return toResponse(post, userId);
    }

    // Host accepts a dispute: the person is marked present.
    @Transactional
    public List<AttendanceRow> acceptDispute(UUID hostId, UUID postId, UUID joinId) {
        requireHostedActivity(hostId, postId);
        PostJoinRequest join = joinRepository.findByIdAndPostId(joinId, postId)
                .orElseThrow(() -> new ResourceNotFoundException("Join request not found: " + joinId));
        ActivityAttendance attendance = attendanceRepository.findByJoinRequestId(joinId)
                .filter(a -> a.getDisputeStatus() == DisputeStatus.OPEN)
                .orElseThrow(() -> new BadRequestException("There's no open dispute for this person"));
        attendance.setDisputeStatus(DisputeStatus.ACCEPTED);
        attendance.setDisputeResolvedAt(Instant.now());
        attendanceRepository.save(attendance);
        join.setOutcome(PostJoinOutcome.ATTENDED);
        joinRepository.save(join);
        notificationService.notifySystem(join.getUser(), "Dispute accepted", "The host agreed: you're marked present.");
        return attendance(hostId, postId);
    }

    // --- G12: private feedback ---------------------------------------------------------------

    // Host -> someone who joined, or someone who joined -> host. One note per pair, editable.
    @Transactional
    public FeedbackResponse giveFeedback(UUID fromId, UUID postId, UUID toId, String text) {
        Post post = requireActivity(postId);
        if (fromId.equals(toId)) throw new BadRequestException("You can't leave feedback for yourself");
        if (post.getStartsAt() == null || post.getStartsAt().isAfter(Instant.now())) {
            throw new BadRequestException("Feedback opens once the activity has started");
        }
        UUID hostId = post.getAuthorUser().getId();
        boolean allowed = (fromId.equals(hostId) && joined(postId, toId)) || (toId.equals(hostId) && joined(postId, fromId));
        if (!allowed) throw new AccessDeniedException("Feedback is only between the host and people who joined");
        User from = requireUser(fromId), to = requireUser(toId);
        ActivityFeedback feedback = feedbackRepository.findByPostIdAndFromUserIdAndToUserId(postId, fromId, toId)
                .orElseGet(() -> ActivityFeedback.builder().post(post).fromUser(from).toUser(to).build());
        boolean isNew = feedback.getId() == null;
        feedback.setText(text.trim());
        feedback = feedbackRepository.save(feedback);
        if (isNew) {
            notificationService.notifySystem(to, "Private feedback", from.getName() + " left you private feedback about an activity.");
        }
        return toFeedback(feedback);
    }

    @Transactional(readOnly = true)
    public Page<FeedbackResponse> receivedFeedback(UUID userId, Pageable pageable) {
        return feedbackRepository.findByToUserIdOrderByCreatedAtDescIdDesc(userId, pageable).map(this::toFeedback);
    }

    // --- helpers -----------------------------------------------------------------------------

    private void saveAnswers(Post post, User user, List<AnswerInput> answers) {
        List<ActivityQuestion> questions = questionRepository.findByPostIdOrderByPositionAsc(post.getId());
        Map<UUID, String> given = new HashMap<>();
        for (AnswerInput a : answers == null ? List.<AnswerInput>of() : answers) {
            given.put(a.questionId(), a.answer() == null ? "" : a.answer().trim());
        }
        Set<UUID> known = questions.stream().map(ActivityQuestion::getId).collect(Collectors.toSet());
        if (!known.containsAll(given.keySet())) throw new BadRequestException("One of the answers is for a question this activity doesn't ask");
        for (ActivityQuestion q : questions) {
            String answer = given.getOrDefault(q.getId(), "");
            if (answer.isEmpty()) {
                if (q.isRequired()) throw new BadRequestException("Please answer: " + q.getText());
                continue;
            }
            ActivityAnswer row = answerRepository.findForPostAndUser(post.getId(), user.getId()).stream()
                    .filter(existing -> existing.getQuestion().getId().equals(q.getId())).findFirst()
                    .orElseGet(() -> ActivityAnswer.builder().question(q).user(user).build());
            row.setAnswer(answer);
            answerRepository.save(row);
        }
    }

    private ActivityResponse toResponse(Post post, UUID viewerId) {
        ActivityDetails details = detailsRepository.findByPostId(post.getId()).orElse(null);
        List<QuestionResponse> questions = questionRepository.findByPostIdOrderByPositionAsc(post.getId()).stream()
                .map(q -> new QuestionResponse(q.getId().toString(), q.getText(), q.isRequired())).toList();
        Integer spotsLeft = post.getCapacity() == null ? null : Math.max(0, post.getCapacity() - post.getSpotsFilled());
        long waiting = waitlistRepository.countByPostIdAndStatus(post.getId(), WaitlistStatus.WAITING);
        return new ActivityResponse(post.getId().toString(),
                details == null ? null : details.getKind().wireValue(),
                details == null ? Map.of() : readDetails(details),
                details == null ? null : fileSigningService.sign(details.getCoverUrl()),
                details == null || details.isWaitlistEnabled(),
                questions, spotsLeft, waiting, viewerId == null ? null : viewerState(post, viewerId, !questions.isEmpty()));
    }

    private ViewerState viewerState(Post post, UUID viewerId, boolean hasQuestions) {
        PostJoinRequest join = joinRepository.findByPostIdAndUserId(post.getId(), viewerId)
                .filter(j -> j.getStatus() != PostJoinStatus.WITHDRAWN).orElse(null);
        Integer position = waitlistRepository.findByPostIdAndUserId(post.getId(), viewerId)
                .filter(w -> w.getStatus() == WaitlistStatus.WAITING)
                .map(w -> (int) waitlistRepository.countByPostIdAndStatusAndJoinedAtBefore(post.getId(), WaitlistStatus.WAITING, w.getJoinedAt()) + 1)
                .orElse(null);
        ActivityAttendance attendance = join == null ? null : attendanceRepository.findByJoinRequestId(join.getId()).orElse(null);
        boolean answered = hasQuestions && !answerRepository.findForPostAndUser(post.getId(), viewerId).isEmpty();
        String disputeOpenUntil = null;
        if (join != null && join.getOutcome() == PostJoinOutcome.NO_SHOW && attendance != null
                && attendance.getDisputeStatus() == DisputeStatus.NONE && attendance.getOutcomeRecordedAt() != null) {
            Instant until = attendance.getOutcomeRecordedAt().plus(ActivityRules.DISPUTE_WINDOW);
            if (until.isAfter(Instant.now())) disputeOpenUntil = until.toString();
        }
        return new ViewerState(isHost(post, viewerId),
                join == null ? null : join.getStatus().wireValue(),
                position, answered,
                attendance == null || attendance.getCheckedInAt() == null ? null : attendance.getCheckedInAt().toString(),
                join == null || join.getOutcome() == null ? null : join.getOutcome().wireValue(),
                attendance == null ? null : attendance.getDisputeStatus().wireValue(),
                disputeOpenUntil);
    }

    private Map<String, String> cleanDetails(ActivityKind kind, Map<String, String> raw) {
        LinkedHashMap<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : raw.entrySet()) {
            String key = e.getKey() == null ? "" : e.getKey().trim();
            String value = e.getValue() == null ? "" : e.getValue().trim();
            if (value.isEmpty()) continue;
            if (!kind.accepts(key)) {
                throw new BadRequestException("'" + key + "' isn't a detail for a " + kind.wireValue() + " activity");
            }
            if (value.length() > MAX_DETAIL_VALUE) throw new BadRequestException("'" + key + "' can be at most " + MAX_DETAIL_VALUE + " characters");
            ProtectedAttributes.reject("the activity details", value);
            out.put(key, value);
        }
        if (out.size() > MAX_DETAIL_KEYS) throw new BadRequestException("Too many details");
        return out;
    }

    private Map<String, String> readDetails(ActivityDetails details) {
        try {
            return objectMapper.readValue(details.getDetailsJson(), new TypeReference<LinkedHashMap<String, String>>() { });
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Invalid stored activity details", e);
        }
    }

    private String writeDetails(Map<String, String> details) {
        try {
            return objectMapper.writeValueAsString(details);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Could not store activity details", e);
        }
    }

    private ActivityDetails requireDetails(Post post) {
        return detailsRepository.findByPostId(post.getId())
                .orElseThrow(() -> new BadRequestException("Set the activity kind first (PUT /activities/{id}/details)"));
    }

    private ActivityAttendance attendanceFor(PostJoinRequest join) {
        return attendanceRepository.findByJoinRequestId(join.getId())
                .orElseGet(() -> ActivityAttendance.builder().joinRequest(join).build());
    }

    private boolean joined(UUID postId, UUID userId) {
        return joinRepository.findByPostIdAndUserId(postId, userId).filter(j -> j.getStatus() == PostJoinStatus.APPROVED).isPresent();
    }

    private FeedbackResponse toFeedback(ActivityFeedback f) {
        Post post = f.getPost();
        String activity = post.getTitle() != null ? post.getTitle()
                : (post.getBody().length() > 60 ? post.getBody().substring(0, 60) + "…" : post.getBody());
        return new FeedbackResponse(f.getId().toString(), post.getId().toString(), activity,
                f.getFromUser().getId().toString(), f.getFromUser().getName(), f.getText(), f.getCreatedAt().toString());
    }

    private Post requireActivity(UUID postId) {
        Post post = postRepository.findById(postId).orElseThrow(() -> notFound(postId));
        requireActivity(post);
        return post;
    }

    private void requireActivity(Post post) {
        if (post.getIntentType() != PostIntentType.ACTIVITY || post.isAnonymous() || post.getRemovedReason() != null) {
            throw new ResourceNotFoundException("Activity not found: " + post.getId());
        }
    }

    private Post requireHostedActivity(UUID hostId, UUID postId) {
        Post post = requireActivity(postId);
        if (!isHost(post, hostId)) throw new AccessDeniedException("Not your activity");
        return post;
    }

    private static boolean isHost(Post post, UUID userId) {
        return post.getAuthorUser().getId().equals(userId);
    }

    private User requireUser(UUID userId) {
        return userRepository.findById(userId).orElseThrow(() -> new ResourceNotFoundException("Account not found"));
    }

    private static ResourceNotFoundException notFound(UUID postId) {
        return new ResourceNotFoundException("Activity not found: " + postId);
    }
}
