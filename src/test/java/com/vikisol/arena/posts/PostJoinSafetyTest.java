package com.vikisol.arena.posts;

import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.posts.entity.*;
import com.vikisol.arena.posts.repository.*;
import com.vikisol.arena.posts.service.PostService;
import com.vikisol.arena.schema.EmbeddedPostgresAppTest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.*;

class PostJoinSafetyTest extends EmbeddedPostgresAppTest {
    @Autowired UserRepository users;
    @Autowired PostRepository posts;
    @Autowired PostJoinRequestRepository joins;
    @Autowired PostService service;
    @Autowired PlatformTransactionManager transactions;

    User user() {
        return users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x")
                .name("Participant").role(Role.TALENT).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
    }
    Post post(User host, int capacity) {
        return posts.save(Post.builder().authorUser(host).intentType(PostIntentType.ACTIVITY)
                .body("Test activity").capacity(capacity).startsAt(Instant.now().plusSeconds(3600))
                .visibility(PostVisibility.APPROVAL).build());
    }
    PostJoinRequest pending(Post post, User participant) {
        return joins.save(PostJoinRequest.builder().post(post).user(participant).build());
    }

    @Test void cannotApproveAnotherPostsRequest() {
        var host = user(); var owned = post(host, 2); var other = post(user(), 2);
        var request = pending(other, user());
        assertThatThrownBy(() -> service.decideJoin(host.getId(), owned.getId(), request.getId(), true))
                .hasMessageContaining("Join request not found");
        assertThat(request.getStatus()).isEqualTo(PostJoinStatus.PENDING);
        assertThat(owned.getSpotsFilled()).isZero();
    }

    @Test void joinedPostsBelongOnlyToTheCaller() {
        var host = user();
        var activity = posts.save(Post.builder().authorUser(host).intentType(PostIntentType.ACTIVITY)
                .body("Evening game").visibility(PostVisibility.PUBLIC).build());
        var member = user();
        var someoneElse = user();
        joins.save(PostJoinRequest.builder().post(activity).user(member).status(PostJoinStatus.APPROVED).build());

        assertThat(service.getJoined(member.getId(), org.springframework.data.domain.PageRequest.of(0, 20)).content())
                .extracting(com.vikisol.arena.posts.dto.PostResponse::id)
                .contains(activity.getId().toString());
        assertThat(service.getJoined(someoneElse.getId(), org.springframework.data.domain.PageRequest.of(0, 20)).content())
                .isEmpty();
    }

    @Test void onlyTheOwnerCanResolveANeed() {
        var owner = user();
        var other = user();
        var ask = posts.save(Post.builder().authorUser(owner).intentType(PostIntentType.ASK).body("Need a hand").build());
        assertThatThrownBy(() -> service.closeAsResolved(other.getId(), ask.getId()))
                .isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThat(service.closeAsResolved(owner.getId(), ask.getId()).status()).isEqualTo("closed");
    }

    @Test void leavingFreesAFullActivityAndAllowsRejoining() {
        var host = user();
        var activity = post(host, 1);
        activity.setVisibility(PostVisibility.PUBLIC);
        var participant = user();
        service.requestJoin(participant.getId(), activity.getId());
        assertThat(posts.findById(activity.getId()).orElseThrow().getStatus()).isEqualTo(PostStatus.FULL);

        service.withdrawJoin(participant.getId(), activity.getId());
        var reopened = posts.findById(activity.getId()).orElseThrow();
        assertThat(reopened.getSpotsFilled()).isZero();
        assertThat(reopened.getStatus()).isEqualTo(PostStatus.OPEN);
        assertThat(joins.findByPostIdAndUserId(activity.getId(), participant.getId()).orElseThrow().getStatus())
                .isEqualTo(PostJoinStatus.WITHDRAWN);

        service.requestJoin(participant.getId(), activity.getId());
        assertThat(posts.findById(activity.getId()).orElseThrow().getSpotsFilled()).isEqualTo(1);
    }

    @Test void withdrawingAPendingRequestDoesNotTakeASpot() {
        var host = user();
        var activity = post(host, 2);
        var participant = user();
        pending(activity, participant);
        service.withdrawJoin(participant.getId(), activity.getId());
        assertThat(joins.findByPostIdAndUserId(activity.getId(), participant.getId()).orElseThrow().getStatus())
                .isEqualTo(PostJoinStatus.WITHDRAWN);
        assertThat(activity.getSpotsFilled()).isZero();
        assertThatThrownBy(() -> service.withdrawJoin(user().getId(), activity.getId()))
                .hasMessageContaining("haven't requested");
    }

    @Test void hostRecordsAttendanceOnlyAfterStartAndOnlyOnTheirPost() {
        var host = user();
        var activity = post(host, 2);
        var participant = user();
        var request = pending(activity, participant);
        service.decideJoin(host.getId(), activity.getId(), request.getId(), true);
        assertThatThrownBy(() -> service.recordOutcome(host.getId(), activity.getId(), request.getId(), "attended"))
                .hasMessageContaining("after the activity starts");
        activity.setStartsAt(Instant.now().minusSeconds(60));
        var recorded = service.recordOutcome(host.getId(), activity.getId(), request.getId(), "no_show");
        assertThat(recorded.outcome()).isEqualTo("no_show");
        var someoneElse = user();
        var other = post(someoneElse, 2);
        assertThatThrownBy(() -> service.recordOutcome(host.getId(), other.getId(), request.getId(), "attended"))
                .hasMessageContaining("Not your post");
    }

    @Test void cannotApproveWhenFullCancelledOrStarted() {
        var host = user(); var activity = post(host, 1); var request = pending(activity, user());
        activity.setSpotsFilled(1);
        assertThatThrownBy(() -> service.decideJoin(host.getId(), activity.getId(), request.getId(), true))
                .hasMessageContaining("full");
        activity.setSpotsFilled(0); activity.setStatus(PostStatus.CANCELLED);
        assertThatThrownBy(() -> service.decideJoin(host.getId(), activity.getId(), request.getId(), true))
                .hasMessageContaining("no longer open");
        activity.setStatus(PostStatus.OPEN); activity.setStartsAt(Instant.now().minusSeconds(1));
        assertThatThrownBy(() -> service.decideJoin(host.getId(), activity.getId(), request.getId(), true))
                .hasMessageContaining("already started");
        assertThat(request.getStatus()).isEqualTo(PostJoinStatus.PENDING);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    void concurrentApprovalsCannotOverfillOneRemainingSpot() throws Exception {
        var tx = new TransactionTemplate(transactions);
        UUID[] ids = tx.execute(status -> {
            var host = user(); var activity = post(host, 1);
            return new UUID[]{host.getId(), activity.getId(), pending(activity, user()).getId(), pending(activity, user()).getId()};
        });
        var gate = new CountDownLatch(1);
        try (var pool = Executors.newFixedThreadPool(2)) {
            var first = pool.submit(() -> approveAfter(gate, ids, 2));
            var second = pool.submit(() -> approveAfter(gate, ids, 3));
            gate.countDown();
            assertThat(java.util.List.of(first.get(15, TimeUnit.SECONDS), second.get(15, TimeUnit.SECONDS)))
                    .containsExactlyInAnyOrder(true, false);
            tx.executeWithoutResult(status -> {
                assertThat(posts.findById(ids[1]).orElseThrow().getSpotsFilled()).isEqualTo(1);
                assertThat(joins.countByPostIdAndStatus(ids[1], PostJoinStatus.APPROVED)).isEqualTo(1);
            });
        }
    }
    boolean approveAfter(CountDownLatch gate, UUID[] ids, int index) throws InterruptedException {
        gate.await();
        try { service.decideJoin(ids[0], ids[1], ids[index], true); return true; }
        catch (com.vikisol.arena.common.exception.BadRequestException expected) { return false; }
    }
}
