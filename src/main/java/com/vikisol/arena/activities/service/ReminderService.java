package com.vikisol.arena.activities.service;

import com.vikisol.arena.activities.entity.PostReminder;
import com.vikisol.arena.activities.repository.PostReminderRepository;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.common.exception.ResourceNotFoundException;
import com.vikisol.arena.notifications.service.NotificationService;
import com.vikisol.arena.posts.entity.Post;
import com.vikisol.arena.posts.entity.PostJoinStatus;
import com.vikisol.arena.posts.entity.PostStatus;
import com.vikisol.arena.posts.repository.PostJoinRequestRepository;
import com.vikisol.arena.posts.repository.PostRepository;
import com.vikisol.arena.auth.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

// FE-API-GAPS row 7 / flow §3: reminders before an activity. Joiners get 24h and 2h reminders
// when approved; anyone involved (host or joined) can add or remove their own.
@Service
@RequiredArgsConstructor
public class ReminderService {

    static final List<Integer> DEFAULT_MINUTES = List.of(24 * 60, 120);

    private final PostReminderRepository reminderRepository;
    private final PostRepository postRepository;
    private final PostJoinRequestRepository joinRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    @Transactional
    public List<Integer> add(UUID userId, UUID postId, int minutesBefore) {
        Post post = postRepository.findById(postId).orElseThrow(() -> new ResourceNotFoundException("Post not found: " + postId));
        boolean involved = post.getAuthorUser().getId().equals(userId) || joinRepository.findByPostIdAndUserId(postId, userId)
                .filter(j -> j.getStatus() == PostJoinStatus.APPROVED).isPresent();
        if (!involved) throw new AccessDeniedException("Reminders are for the host and people who joined");
        if (post.getStartsAt() == null) throw new BadRequestException("This has no start time to remind you about");
        Instant at = post.getStartsAt().minus(Duration.ofMinutes(minutesBefore));
        if (!at.isAfter(Instant.now())) throw new BadRequestException("That reminder time has already passed");
        schedule(post, userRepository.getReferenceById(userId), minutesBefore);
        return mine(userId, postId);
    }

    @Transactional
    public List<Integer> remove(UUID userId, UUID postId, Integer minutesBefore) {
        reminderRepository.findByPostIdAndUserId(postId, userId).stream()
                .filter(r -> minutesBefore == null || r.getMinutesBefore() == minutesBefore)
                .forEach(reminderRepository::delete);
        return mine(userId, postId);
    }

    @Transactional(readOnly = true)
    public List<Integer> mine(UUID userId, UUID postId) {
        return reminderRepository.findByPostIdAndUserId(postId, userId).stream()
                .filter(r -> r.getSentAt() == null).map(PostReminder::getMinutesBefore).sorted().toList();
    }

    // Called by PostService when someone is approved into an activity.
    @Transactional
    public void addDefaults(Post post, User user) {
        if (post.getStartsAt() == null) return;
        for (int minutes : DEFAULT_MINUTES) {
            if (post.getStartsAt().minus(Duration.ofMinutes(minutes)).isAfter(Instant.now())) schedule(post, user, minutes);
        }
    }

    // Called when the start time changes: unsent reminders follow it.
    @Transactional
    public void reschedule(Post post) {
        for (PostReminder r : reminderRepository.findByPostIdAndSentAtIsNull(post.getId())) {
            if (post.getStartsAt() == null) {
                reminderRepository.delete(r);
            } else {
                r.setRemindAt(post.getStartsAt().minus(Duration.ofMinutes(r.getMinutesBefore())));
                reminderRepository.save(r);
            }
        }
    }

    @Scheduled(fixedDelayString = "${app.reminders.interval-ms:60000}")
    @Transactional
    public void sendDue() {
        for (PostReminder r : reminderRepository.findTop200BySentAtIsNullAndRemindAtLessThanEqualOrderByRemindAtAsc(Instant.now())) {
            r.setSentAt(Instant.now());
            reminderRepository.save(r);
            Post post = r.getPost();
            if (post.getStatus() != PostStatus.OPEN && post.getStatus() != PostStatus.FULL) continue;
            String when = r.getMinutesBefore() >= 60 ? (r.getMinutesBefore() / 60) + "h" : r.getMinutesBefore() + " min";
            String title = post.getTitle() != null ? post.getTitle() : post.getBody();
            notificationService.notifySystem(r.getUser(), "Reminder",
                    "\"" + (title.length() > 60 ? title.substring(0, 60) + "…" : title) + "\" starts in " + when + ".");
        }
    }

    private void schedule(Post post, User user, int minutesBefore) {
        PostReminder r = reminderRepository.findByPostIdAndUserId(post.getId(), user.getId()).stream()
                .filter(x -> x.getMinutesBefore() == minutesBefore).findFirst()
                .orElseGet(() -> PostReminder.builder().post(post).user(user).minutesBefore(minutesBefore).build());
        r.setRemindAt(post.getStartsAt().minus(Duration.ofMinutes(minutesBefore)));
        r.setSentAt(null);
        reminderRepository.save(r);
    }
}
