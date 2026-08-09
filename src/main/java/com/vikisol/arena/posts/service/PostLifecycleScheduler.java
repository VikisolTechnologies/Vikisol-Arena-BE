package com.vikisol.arena.posts.service;

import com.vikisol.arena.notifications.service.NotificationService;
import com.vikisol.arena.posts.entity.Post;
import com.vikisol.arena.posts.entity.PostIntentType;
import com.vikisol.arena.posts.entity.PostStatus;
import com.vikisol.arena.posts.repository.PostRepository;
import com.vikisol.arena.rooms.entity.RoomMember;
import com.vikisol.arena.rooms.repository.RoomMemberRepository;
import com.vikisol.arena.rooms.repository.RoomRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;

/**
 * ARENA-V2-PRODUCT-ARCHITECTURE.md Phase A/B activity lifecycle: "reminders before start,
 * cancellation with notifications, auto-expiry." Cancellation lives in PostService.cancel()
 * (an author-initiated action, not scheduled); this handles the two genuinely time-driven
 * pieces. First scheduled-job infrastructure in this codebase - see ArenaApiApplication's
 * @EnableScheduling.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class PostLifecycleScheduler {

    private static final Duration REMINDER_WINDOW = Duration.ofHours(1);
    private static final Duration STALE_AFTER = Duration.ofHours(2);

    private final PostRepository postRepository;
    private final RoomRepository roomRepository;
    private final RoomMemberRepository roomMemberRepository;
    private final NotificationService notificationService;

    // Every 5 minutes - frequent enough that "starts within the hour" reminders don't slip
    // past their own window, cheap enough (a small indexed query) not to matter at this scale.
    @Scheduled(fixedRate = 5 * 60 * 1000)
    @Transactional
    public void remindUpcomingActivities() {
        Instant now = Instant.now();
        List<Post> due = postRepository.findDueForReminder(PostStatus.OPEN, PostIntentType.ACTIVITY, now, now.plus(REMINDER_WINDOW));
        for (Post post : due) {
            roomRepository.findByPostId(post.getId()).ifPresent(room -> {
                for (RoomMember member : roomMemberRepository.findByRoomId(room.getId())) {
                    notificationService.notifyActivityStartingSoon(member.getUser(), post);
                }
            });
            post.setRemindedAt(now);
            postRepository.save(post);
        }
        if (!due.isEmpty()) {
            log.info("PostLifecycleScheduler: reminded {} upcoming activities", due.size());
        }
    }

    // Every 15 minutes - expiry doesn't need reminder-job precision, just needs to eventually
    // stop showing stale posts as OPEN in the Feed/Map.
    @Scheduled(fixedRate = 15 * 60 * 1000)
    @Transactional
    public void expireStalePosts() {
        Instant now = Instant.now();
        List<Post> expirable = postRepository.findExpirable(List.of(PostStatus.OPEN, PostStatus.FULL), now, now.minus(STALE_AFTER));
        for (Post post : expirable) {
            post.setStatus(PostStatus.EXPIRED);
        }
        if (!expirable.isEmpty()) {
            postRepository.saveAll(expirable);
            log.info("PostLifecycleScheduler: expired {} stale posts", expirable.size());
        }
    }
}
