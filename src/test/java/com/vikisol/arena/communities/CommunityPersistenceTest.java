package com.vikisol.arena.communities;

import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.communities.entity.Community;
import com.vikisol.arena.communities.entity.CommunityMember;
import com.vikisol.arena.communities.entity.CommunityRole;
import com.vikisol.arena.communities.repository.CommunityMemberRepository;
import com.vikisol.arena.communities.repository.CommunityRepository;
import com.vikisol.arena.posts.entity.*;
import com.vikisol.arena.posts.repository.PostCommentRepository;
import com.vikisol.arena.posts.repository.PostReactionRepository;
import com.vikisol.arena.posts.repository.PostRepository;
import com.vikisol.arena.schema.EmbeddedPostgresTest;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** Phase 2 (Discuss) queries run against real Postgres: communities, votes and threaded replies. */
class CommunityPersistenceTest extends EmbeddedPostgresTest {

    @Autowired private UserRepository users;
    @Autowired private CommunityRepository communities;
    @Autowired private CommunityMemberRepository members;
    @Autowired private PostRepository posts;
    @Autowired private PostReactionRepository reactions;
    @Autowired private PostCommentRepository comments;
    @Autowired private EntityManager em;

    private User user(String name) {
        return users.save(User.builder().email(name + "-" + UUID.randomUUID() + "@test.local").passwordHash("x").name(name).role(Role.TALENT).build());
    }

    private Post post(User author, PostIntentType type, Community community) {
        return posts.save(Post.builder().authorUser(author).intentType(type).body("Anyone up for " + type).community(community).build());
    }

    @Test
    void communityCountsMembershipAndThreads() {
        User owner = user("owner"), alice = user("alice"), banned = user("banned");
        Community c = communities.save(Community.builder().slug("test-" + UUID.randomUUID().toString().substring(0, 8)).name("Test").emoji("💬").createdBy(owner).build());
        members.save(CommunityMember.builder().community(c).user(owner).role(CommunityRole.OWNER).build());
        members.save(CommunityMember.builder().community(c).user(alice).role(CommunityRole.MEMBER).build());
        members.save(CommunityMember.builder().community(c).user(banned).role(CommunityRole.MEMBER).banned(true).build());
        Post inCommunity = post(alice, PostIntentType.ASK, c);
        post(alice, PostIntentType.UPDATE, null);
        post(alice, PostIntentType.ACTIVITY, null);
        em.flush();

        assertThat(members.countMembers(Set.of(c.getId()))).singleElement()
                .satisfies(row -> assertThat(row.getCnt()).isEqualTo(2)); // banned member not counted
        assertThat(members.findMine(alice.getId(), Set.of(c.getId()))).hasSize(1);
        assertThat(posts.countByCommunity(Set.of(c.getId()), List.of(PostStatus.OPEN))).singleElement()
                .satisfies(row -> assertThat(row.getCnt()).isEqualTo(1));
        assertThat(posts.findByCommunity(c.getId(), List.of(PostStatus.OPEN), PageRequest.of(0, 10)).getContent())
                .extracting(Post::getId).containsExactly(inCommunity.getId());
        assertThat(posts.findDiscussions(List.of(PostStatus.OPEN), List.of(PostIntentType.ASK, PostIntentType.UPDATE), PageRequest.of(0, 50)).getContent())
                .allMatch(p -> p.getIntentType() != PostIntentType.ACTIVITY);

        // Removing a (demo) community: detach its posts, drop members, then the community itself.
        posts.detachFromCommunity(c.getId());
        members.deleteByCommunityId(c.getId());
        communities.delete(c);
        em.flush();
        em.clear();
        assertThat(posts.findById(inCommunity.getId())).get().extracting(Post::getCommunity).isNull();
        assertThat(communities.findById(c.getId())).isEmpty();
    }

    @Test
    void votesSumToScoreAndOnlyUpvotesCountAsLikes() {
        User author = user("author"), a = user("a"), b = user("b"), d = user("d");
        Post p = post(author, PostIntentType.ASK, null);
        reactions.save(PostReaction.builder().post(p).user(a).value((short) 1).build());
        reactions.save(PostReaction.builder().post(p).user(b).value((short) 1).build());
        reactions.save(PostReaction.builder().post(p).user(d).value((short) -1).build());
        em.flush();

        assertThat(reactions.sumValueByPostIdIn(List.of(p.getId()))).singleElement().satisfies(r -> assertThat(r.getCnt()).isEqualTo(1));
        assertThat(reactions.countByPostIdIn(List.of(p.getId()))).singleElement().satisfies(r -> assertThat(r.getCnt()).isEqualTo(2));
        assertThat(reactions.findMyVotes(d.getId(), List.of(p.getId()))).singleElement().satisfies(v -> assertThat(v.getValue()).isEqualTo((short) -1));
        assertThat(reactions.findReactedPostIdsByUserIdAndPostIdIn(d.getId(), List.of(p.getId()))).isEmpty();
    }

    @Test
    void deletingAPostsCommentsHandlesReplyChains() {
        User author = user("author"), other = user("other");
        Post p = post(author, PostIntentType.ASK, null);
        PostComment root = comments.save(PostComment.builder().post(p).authorUser(other).content("root").build());
        PostComment reply = comments.save(PostComment.builder().post(p).authorUser(author).content("reply").parentComment(root).build());
        comments.save(PostComment.builder().post(p).authorUser(other).content("reply to reply").parentComment(reply).build());
        em.flush();

        assertThat(comments.existsByParentCommentId(root.getId())).isTrue();
        comments.deleteByPostId(p.getId());
        assertThat(comments.findTop200ByPostIdOrderByCreatedAtDesc(p.getId())).isEmpty();
    }
}
