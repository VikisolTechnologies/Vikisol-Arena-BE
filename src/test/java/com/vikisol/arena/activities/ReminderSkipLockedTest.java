package com.vikisol.arena.activities;

import com.vikisol.arena.activities.entity.PostReminder;
import com.vikisol.arena.activities.repository.PostReminderRepository;
import com.vikisol.arena.activities.service.ReminderService;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.notifications.repository.NotificationRepository;
import com.vikisol.arena.posts.entity.Post;
import com.vikisol.arena.posts.entity.PostIntentType;
import com.vikisol.arena.posts.entity.PostVisibility;
import com.vikisol.arena.posts.repository.PostRepository;
import com.vikisol.arena.schema.EmbeddedPostgresAppTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

// ARCHITECT-REVIEW-BE-1 SHOULD-FIX: ReminderService.sendDue had no locking, so a second replica's
// scheduler tick running at the same moment could pick up and double-send the same due reminder.
// lockNextDueBatch's FOR UPDATE SKIP LOCKED means a concurrent run claims nothing already held.
class ReminderSkipLockedTest extends EmbeddedPostgresAppTest {

    @Autowired UserRepository users;
    @Autowired PostRepository posts;
    @Autowired PostReminderRepository reminders;
    @Autowired NotificationRepository notifications;
    @Autowired ReminderService reminderService;
    @Autowired PlatformTransactionManager transactions;

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void aSecondConcurrentRunNeverClaimsAReminderTheFirstAlreadyLocked() throws Exception {
        var tx = new TransactionTemplate(transactions);
        record Ids(UUID userId, UUID postId) {}
        Ids ids = tx.execute(status -> {
            User host = users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("Host")
                    .role(Role.TALENT).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
            User joiner = users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x").name("Joiner")
                    .role(Role.TALENT).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
            Post p = posts.save(Post.builder().authorUser(host).intentType(PostIntentType.ACTIVITY).body("Yoga")
                    .visibility(PostVisibility.PUBLIC).startsAt(Instant.now().plus(1, ChronoUnit.HOURS)).build());
            reminders.save(PostReminder.builder().post(p).user(joiner).minutesBefore(60)
                    .remindAt(Instant.now().minusSeconds(5)).build());
            return new Ids(joiner.getId(), p.getId());
        });

        var holdLock = new CountDownLatch(1);
        var secondRunStarted = new CountDownLatch(1);
        var release = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var holder = pool.submit(() -> {
                tx.executeWithoutResult(status -> {
                    reminders.lockNextDueBatch(Instant.now()); // claims and holds the row's lock
                    holdLock.countDown();
                    try {
                        secondRunStarted.await(5, TimeUnit.SECONDS);
                        release.await(5, TimeUnit.SECONDS);
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                });
            });
            holdLock.await(5, TimeUnit.SECONDS);
            var contender = pool.submit(() -> {
                secondRunStarted.countDown();
                reminderService.sendDue(); // must skip the locked row, not block or double-send
                return true;
            });
            assertThat(contender.get(15, TimeUnit.SECONDS)).isTrue();
            release.countDown();
            holder.get(15, TimeUnit.SECONDS);
        }

        tx.executeWithoutResult(status -> {
            PostReminder r = reminders.findByPostIdAndUserId(ids.postId(), ids.userId()).get(0);
            assertThat(r.getSentAt()).isNull(); // the concurrent run skipped it rather than claiming it
            assertThat(notifications.findByUserIdOrderByCreatedAtDesc(ids.userId(), PageRequest.of(0, 20)).getContent()).isEmpty();
        });
    }
}
