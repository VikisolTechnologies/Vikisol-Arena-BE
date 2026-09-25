package com.vikisol.arena.seed;

import com.vikisol.arena.posts.entity.Post;
import com.vikisol.arena.posts.entity.PostIntentType;
import com.vikisol.arena.posts.entity.PostStatus;
import com.vikisol.arena.posts.repository.PostRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * DemoContentService seeds activities with startsAt = seed time + a few hours, so a day later
 * every demo activity has started, PostLifecycleScheduler has expired it, and the Feed and Nearby
 * map go empty of activities - the one content type Nearby exists for. This keeps the demo
 * overlay "live": any demo activity that has expired (or is about to start) gets pushed forward
 * to a fresh start time within the next three days and reopened.
 *
 * Demo content only (demoContent = true) - real posts are never touched - and the whole bean is
 * absent when app.demo-content.enabled is off, same gate as DemoContentController. Cancelled and
 * closed demo posts stay as they are; those are deliberate edge-case states in the seed.
 */
@Component
@RequiredArgsConstructor
@Slf4j
@ConditionalOnProperty(value = "app.demo-content.enabled", havingValue = "true")
public class DemoActivityRefresher {

    // Refresh a little before the start time, so an activity never sits in the "already
    // started" window where it's still OPEN but no longer joinable in any useful sense.
    private static final Duration START_BUFFER = Duration.ofHours(1);
    private static final Set<PostStatus> REFRESHABLE = Set.of(PostStatus.OPEN, PostStatus.FULL, PostStatus.EXPIRED);

    private final PostRepository postRepository;

    @Scheduled(initialDelay = 60_000, fixedRate = 30 * 60 * 1000)
    @Transactional
    public void refreshDemoActivities() {
        Instant now = Instant.now();
        List<Post> refreshed = new ArrayList<>();
        int index = 0;
        for (Post post : postRepository.findByDemoContentTrue()) {
            if (post.getIntentType() != PostIntentType.ACTIVITY || !REFRESHABLE.contains(post.getStatus())) continue;
            boolean stale = post.getStatus() == PostStatus.EXPIRED
                    || (post.getStartsAt() != null && post.getStartsAt().isBefore(now.plus(START_BUFFER)));
            if (!stale) continue;
            // Spread across 3h-72h ahead (deterministic by position, so the set doesn't bunch
            // up at one time) - a mix of "tonight", "tomorrow" and "this weekend".
            long hoursAhead = 3 + (index++ * 7L) % 70;
            post.setStartsAt(now.plus(Duration.ofHours(hoursAhead)));
            post.setEndsAt(null);
            post.setRemindedAt(null);
            boolean full = post.getCapacity() != null && post.getSpotsFilled() >= post.getCapacity();
            post.setStatus(full ? PostStatus.FULL : PostStatus.OPEN);
            refreshed.add(post);
        }
        if (!refreshed.isEmpty()) {
            postRepository.saveAll(refreshed);
            log.info("DemoActivityRefresher: moved {} demo activities to fresh start times", refreshed.size());
        }
    }
}
