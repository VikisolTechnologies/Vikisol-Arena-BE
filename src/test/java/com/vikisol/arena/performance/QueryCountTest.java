package com.vikisol.arena.performance;

import com.vikisol.arena.common.dto.PageLimits;
import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.enterprise.entity.CompanySize;
import com.vikisol.arena.enterprise.entity.EnterpriseProfile;
import com.vikisol.arena.enterprise.repository.EnterpriseProfileRepository;
import com.vikisol.arena.follows.entity.Follow;
import com.vikisol.arena.follows.entity.UserBlock;
import com.vikisol.arena.follows.repository.FollowRepository;
import com.vikisol.arena.follows.repository.UserBlockRepository;
import com.vikisol.arena.follows.service.BlockService;
import com.vikisol.arena.follows.service.FollowService;
import com.vikisol.arena.messaging.entity.Conversation;
import com.vikisol.arena.messaging.repository.ConversationRepository;
import com.vikisol.arena.messaging.service.ConversationService;
import com.vikisol.arena.posts.entity.Post;
import com.vikisol.arena.posts.entity.PostIntentType;
import com.vikisol.arena.posts.entity.PostJoinRequest;
import com.vikisol.arena.posts.entity.PostJoinStatus;
import com.vikisol.arena.posts.entity.PostVisibility;
import com.vikisol.arena.posts.repository.PostJoinRequestRepository;
import com.vikisol.arena.posts.repository.PostRepository;
import com.vikisol.arena.posts.service.PostService;
import com.vikisol.arena.profile.entity.CandidateProfile;
import com.vikisol.arena.profile.entity.ConsentSettings;
import com.vikisol.arena.profile.entity.Industry;
import com.vikisol.arena.profile.repository.CandidateProfileRepository;
import com.vikisol.arena.rooms.entity.Room;
import com.vikisol.arena.rooms.entity.RoomMember;
import com.vikisol.arena.rooms.entity.RoomMessage;
import com.vikisol.arena.rooms.repository.RoomMemberRepository;
import com.vikisol.arena.rooms.repository.RoomMessageRepository;
import com.vikisol.arena.rooms.repository.RoomRepository;
import com.vikisol.arena.rooms.service.RoomService;
import com.vikisol.arena.schema.EmbeddedPostgresAppTest;
import jakarta.persistence.EntityManager;
import jakarta.persistence.EntityManagerFactory;
import org.hibernate.SessionFactory;
import org.hibernate.stat.Statistics;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * N+1 guard: each list call must issue the same number of SQL statements whether it returns a
 * few rows or many. Counted with Hibernate statistics after flushing and clearing the persistence
 * context, so every lazy load really goes to Postgres.
 */
class QueryCountTest extends EmbeddedPostgresAppTest {

    @Autowired EntityManager em;
    @Autowired EntityManagerFactory emf;
    @Autowired UserRepository users;
    @Autowired CandidateProfileRepository profiles;
    @Autowired EnterpriseProfileRepository enterprises;
    @Autowired FollowRepository follows;
    @Autowired UserBlockRepository blocks;
    @Autowired ConversationRepository conversations;
    @Autowired RoomRepository rooms;
    @Autowired RoomMemberRepository roomMembers;
    @Autowired RoomMessageRepository roomMessages;
    @Autowired PostRepository posts;
    @Autowired PostJoinRequestRepository joins;
    @Autowired FollowService followService;
    @Autowired BlockService blockService;
    @Autowired ConversationService conversationService;
    @Autowired RoomService roomService;
    @Autowired PostService postService;

    private Statistics stats;

    @BeforeEach
    void statistics() {
        stats = emf.unwrap(SessionFactory.class).getStatistics();
        assertThat(stats.isStatisticsEnabled()).isTrue();
    }

    @Test
    void followersAndFollowingAreConstantQueries() {
        User me = talent();
        for (int i = 0; i < 2; i++) follow(talent(), me);
        for (int i = 0; i < 2; i++) follow(me, talent());
        long fewFollowers = count(() -> followService.getFollowers(me.getId(), PageLimits.firstPage()));
        long fewFollowing = count(() -> followService.getFollowing(me.getId(), PageLimits.firstPage()));

        for (int i = 0; i < 6; i++) follow(talent(), me);
        for (int i = 0; i < 6; i++) follow(me, talent());
        assertThat(count(() -> followService.getFollowers(me.getId(), PageLimits.firstPage()))).isEqualTo(fewFollowers);
        assertThat(count(() -> followService.getFollowing(me.getId(), PageLimits.firstPage()))).isEqualTo(fewFollowing);
        assertThat(followService.getFollowers(me.getId(), PageLimits.firstPage())).hasSize(8);
    }

    @Test
    void blockListIsConstantQueries() {
        User me = talent();
        for (int i = 0; i < 2; i++) block(me, talent());
        long few = count(() -> blockService.getMyBlocks(me.getId(), PageLimits.firstPage()));
        for (int i = 0; i < 6; i++) block(me, talent());
        assertThat(count(() -> blockService.getMyBlocks(me.getId(), PageLimits.firstPage()))).isEqualTo(few);
    }

    @Test
    void conversationListIsConstantQueries() {
        User me = talent();
        conversation(me, talent());
        conversation(recruiter(), me);
        long few = count(() -> conversationService.getMyConversations(me.getId(), PageLimits.firstPage()));
        for (int i = 0; i < 4; i++) {
            conversation(me, talent());
            conversation(recruiter(), me);
        }
        assertThat(count(() -> conversationService.getMyConversations(me.getId(), PageLimits.firstPage()))).isEqualTo(few);
        assertThat(conversationService.getMyConversations(me.getId(), PageLimits.firstPage())).hasSize(10);
    }

    @Test
    void roomListIsConstantQueries() {
        User me = talent();
        for (int i = 0; i < 2; i++) room(me);
        long few = count(() -> roomService.getMyRooms(me.getId(), PageLimits.firstPage()));
        for (int i = 0; i < 6; i++) room(me);
        assertThat(count(() -> roomService.getMyRooms(me.getId(), PageLimits.firstPage()))).isEqualTo(few);
    }

    @Test
    void joinRequestListIsConstantQueries() {
        User host = talent();
        Post activity = posts.save(Post.builder().authorUser(host).intentType(PostIntentType.ACTIVITY)
                .body("Evening game").visibility(PostVisibility.PUBLIC).build());
        for (int i = 0; i < 2; i++) join(activity, talent());
        long few = count(() -> postService.getJoinRequests(host.getId(), activity.getId(), PageLimits.firstPage()));
        for (int i = 0; i < 6; i++) join(activity, talent());
        assertThat(count(() -> postService.getJoinRequests(host.getId(), activity.getId(), PageLimits.firstPage()))).isEqualTo(few);
    }

    private long count(Supplier<?> call) {
        em.flush();
        em.clear();
        stats.clear();
        call.get();
        return stats.getPrepareStatementCount();
    }

    private User talent() {
        User user = user(Role.TALENT);
        profiles.save(CandidateProfile.builder()
                .user(user).name(user.getName()).avatarEmoji("*").title("Engineer")
                .industry(Industry.ENGINEERING).location("Hyderabad").remote(false)
                .consent(new ConsentSettings(false, true))
                .build());
        return user;
    }

    private User recruiter() {
        User user = user(Role.COMPANY_ADMIN);
        enterprises.save(EnterpriseProfile.builder().user(user).companyName("Acme " + user.getId()).logoEmoji("A")
                .industry(Industry.ENGINEERING).size(CompanySize.S_11_50).build());
        return user;
    }

    private User user(Role role) {
        return users.save(User.builder().email(UUID.randomUUID() + "@test.local").passwordHash("x")
                .name("Person").role(role).dateOfBirth(LocalDate.of(1990, 1, 1)).build());
    }

    private void follow(User follower, User following) {
        follows.save(Follow.builder().followerUser(follower).followingUser(following).build());
    }

    private void block(User blocker, User blocked) {
        blocks.save(UserBlock.builder().blockerUser(blocker).blockedUser(blocked).build());
    }

    private void conversation(User a, User b) {
        conversations.save(Conversation.builder().userA(a).userB(b).lastMessageAt(Instant.now()).build());
    }

    private void room(User member) {
        User host = talent();
        Post post = posts.save(Post.builder().authorUser(host).intentType(PostIntentType.ACTIVITY)
                .body("Meetup").visibility(PostVisibility.PUBLIC).build());
        Room room = rooms.save(Room.builder().post(post).build());
        roomMembers.save(RoomMember.builder().room(room).user(host).build());
        roomMembers.save(RoomMember.builder().room(room).user(member).build());
        roomMessages.save(RoomMessage.builder().room(room).sender(host).content("See you there").build());
    }

    private void join(Post post, User user) {
        joins.save(PostJoinRequest.builder().post(post).user(user).status(PostJoinStatus.PENDING).build());
    }
}
