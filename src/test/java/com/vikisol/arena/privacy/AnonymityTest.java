package com.vikisol.arena.privacy;

import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.exception.BadRequestException;
import com.vikisol.arena.communities.dto.CreateCommunityRequest;
import com.vikisol.arena.communities.service.CommunityService;
import com.vikisol.arena.messaging.dto.ConversationResponse;
import com.vikisol.arena.messaging.dto.CreateConversationRequest;
import com.vikisol.arena.messaging.service.ConversationService;
import com.vikisol.arena.notifications.repository.NotificationRepository;
import com.vikisol.arena.posts.dto.CreatePostRequest;
import com.vikisol.arena.posts.dto.PostCommentResponse;
import com.vikisol.arena.posts.dto.PostResponse;
import com.vikisol.arena.posts.entity.PostIntentType;
import com.vikisol.arena.posts.service.PostCommentService;
import com.vikisol.arena.posts.service.PostService;
import com.vikisol.arena.schema.EmbeddedPostgresAppTest;
import com.vikisol.arena.search.SearchText;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.PageRequest;

import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Phase 2 part C: every way an anonymous author, replier or chat participant could be identified
 * through the API, checked against the real services and a real Postgres.
 */
class AnonymityTest extends EmbeddedPostgresAppTest {

    @Autowired private UserRepository users;
    @Autowired private PostService posts;
    @Autowired private PostCommentService comments;
    @Autowired private ConversationService conversations;
    @Autowired private CommunityService communities;
    @Autowired private NotificationRepository notifications;

    private User user(String name) {
        return users.save(User.builder().email(name.toLowerCase() + "-" + UUID.randomUUID() + "@test.local")
                .passwordHash("x").name(name).role(Role.TALENT).build());
    }

    private static CreatePostRequest ask(String body, boolean anonymous) {
        return request("ask", body, null, null, anonymous);
    }

    private static CreatePostRequest request(String intent, String body, String audience, String communityId, boolean anonymous) {
        return new CreatePostRequest(intent, null, body, null, audience, null, null, null, null, List.of(), List.of(),
                null, null, null, null, communityId, anonymous);
    }

    @Test
    void othersSeeAnAliasTheAuthorSeesThemself() {
        User author = user("Priya Sharma"), reader = user("Reader");
        PostResponse created = posts.create(author.getId(), ask("Is it normal to feel lost after a layoff?", true));

        PostResponse asReader = posts.getPost(UUID.fromString(created.id()), reader.getId());
        assertThat(asReader.anonymous()).isTrue();
        assertThat(asReader.authorUserId()).isEmpty();
        assertThat(asReader.authorName()).startsWith("Anonymous ").doesNotContain("Priya");
        assertThat(asReader.authorAccountAgeDays()).isZero();
        assertThat(asReader.joinable()).isFalse(); // joining would create a room hosted by the author

        PostResponse asGuest = posts.getPost(UUID.fromString(created.id()), null);
        assertThat(asGuest.authorUserId()).isEmpty();
        assertThat(asGuest.authorName()).isEqualTo(asReader.authorName());

        PostResponse asAuthor = posts.getPost(UUID.fromString(created.id()), author.getId());
        assertThat(asAuthor.authorUserId()).isEqualTo(author.getId().toString());
        assertThat(asAuthor.mine()).isTrue();
    }

    @Test
    void anonymousPostsStayOffTheAuthorsProfileAndOutOfNameSearch() {
        User author = user("Kavya Iyer"), reader = user("Reader");
        posts.create(author.getId(), ask("Named question about hiking boots", false));
        posts.create(author.getId(), ask("Anonymous question about salary negotiation", true));

        List<PostResponse> profileAsReader = posts.getUserPosts(author.getId(), reader.getId(), PageRequest.of(0, 20)).content();
        assertThat(profileAsReader).extracting(PostResponse::body).containsExactly("Named question about hiking boots");
        assertThat(posts.getUserPosts(author.getId(), author.getId(), PageRequest.of(0, 20)).content()).hasSize(2);

        var discussions = (java.util.function.Predicate<com.vikisol.arena.posts.entity.Post>) p -> p.getIntentType() == PostIntentType.ASK;
        assertThat(posts.search(reader.getId(), SearchText.terms("kavya"), discussions, 20))
                .extracting(PostResponse::body).containsExactly("Named question about hiking boots");
        assertThat(posts.search(reader.getId(), SearchText.terms("salary negotiation"), discussions, 20)).hasSize(1)
                .allSatisfy(p -> assertThat(p.authorUserId()).isEmpty());
    }

    @Test
    void anonymityIsOnlyForDiscussAndIsLimited() {
        User author = user("Arjun");
        assertThatThrownBy(() -> posts.create(author.getId(), request("activity", "Badminton at 6?", null, null, true)))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> posts.create(author.getId(), request("ask", "Followers only?", "followers", null, true)))
                .isInstanceOf(BadRequestException.class);

        var closed = communities.create(author.getId(), new CreateCommunityRequest("No Masks Allowed", null, null, false));
        assertThatThrownBy(() -> posts.create(author.getId(), request("ask", "In a no-anon community", null, closed.id(), true)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("doesn't allow anonymous");

        for (int i = 0; i < 5; i++) posts.create(author.getId(), ask("Anonymous number " + i, true));
        assertThatThrownBy(() -> posts.create(author.getId(), ask("One too many", true)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("up to 5");
    }

    @Test
    void repliesUseOneAliasPerPersonPerThreadAndTheAuthorStaysAnonymous() {
        User author = user("Meera"), other = user("Rahul"), reader = user("Reader");
        PostResponse post = posts.create(author.getId(), ask("Anonymous: how do I tell my manager I'm burnt out?", true));
        UUID postId = UUID.fromString(post.id());

        // The author asks for a named reply on their own anonymous post - it's forced anonymous.
        comments.addComment(author.getId(), postId, "Thanks all", null, false);
        comments.addComment(other.getId(), postId, "Take leave first", null, true);
        comments.addComment(other.getId(), postId, "And talk to HR", null, true);

        List<PostCommentResponse> asReader = comments.getComments(postId, reader.getId());
        PostCommentResponse opReply = asReader.get(0);
        assertThat(opReply.anonymous()).isTrue();
        assertThat(opReply.op()).isTrue();
        assertThat(opReply.authorUserId()).isNull();
        assertThat(opReply.authorName()).isEqualTo(posts.getPost(postId, reader.getId()).authorName());

        assertThat(asReader.get(1).authorName()).isEqualTo(asReader.get(2).authorName()).isNotEqualTo(opReply.authorName());
        assertThat(asReader).allSatisfy(c -> assertThat(c.authorName()).doesNotContain("Rahul", "Meera"));

        List<PostCommentResponse> asOther = comments.getComments(postId, other.getId());
        assertThat(asOther.get(1).mine()).isTrue();
        assertThat(asOther.get(1).authorUserId()).isEqualTo(other.getId().toString());
    }

    @Test
    void messagingAnAnonymousAuthorHidesThemAndClosingStopsIt() {
        User author = user("Sana"), sender = user("Vikram");
        PostResponse post = posts.create(author.getId(), ask("Anonymous: anyone dealt with a bad landlord?", true));

        // The sender writes under their own name; the anonymous author stays hidden from them.
        ConversationResponse fromSender = conversations.start(sender.getId(), new CreateConversationRequest(null, null, post.id(), false));
        assertThat(fromSender.anonymous()).isTrue();
        assertThat(fromSender.participantId()).isEmpty();
        assertThat(fromSender.participantName()).startsWith("Anonymous ").doesNotContain("Sana");

        conversations.sendMessage(sender.getId(), UUID.fromString(fromSender.id()), "Happened to me - happy to share what worked");
        ConversationResponse asAuthor = conversations.getMyConversations(author.getId()).get(0);
        assertThat(asAuthor.anonymous()).isFalse();
        assertThat(asAuthor.meAnonymous()).isTrue();
        assertThat(asAuthor.participantName()).isEqualTo("Vikram");

        // The author's own named DM with the sender is a different conversation.
        ConversationResponse named = conversations.getOrCreate(author.getId(), sender.getId(), null);
        assertThat(named.id()).isNotEqualTo(fromSender.id());
        assertThat(named.anonymous()).isFalse();

        // Messaging your own anonymous post gets the same answer as any other self-message.
        assertThatThrownBy(() -> conversations.start(author.getId(), new CreateConversationRequest(null, null, post.id(), false)))
                .isInstanceOf(BadRequestException.class).hasMessage("That's your own post.");

        conversations.close(author.getId(), UUID.fromString(fromSender.id()));
        assertThatThrownBy(() -> conversations.sendMessage(sender.getId(), UUID.fromString(fromSender.id()), "hello?"))
                .isInstanceOf(BadRequestException.class);
        assertThatThrownBy(() -> conversations.start(sender.getId(), new CreateConversationRequest(null, null, post.id(), true)))
                .isInstanceOf(BadRequestException.class).hasMessageContaining("isn't accepting");
    }

    @Test
    void aHiddenSenderStaysHiddenInNotifications() {
        User sender = user("Hidden Sender"), recipient = user("Recipient");
        ConversationResponse chat = conversations.start(sender.getId(),
                new CreateConversationRequest(recipient.getId().toString(), null, null, true));
        conversations.sendMessage(sender.getId(), UUID.fromString(chat.id()), "You dropped your keys at the court");

        var sent = notifications.findByUserIdOrderByCreatedAtDesc(recipient.getId(), PageRequest.of(0, 5)).getContent();
        assertThat(sent).isNotEmpty();
        assertThat(sent.get(0).getBody()).doesNotContain("Hidden Sender").contains("anonymous");

        ConversationResponse asRecipient = conversations.getMyConversations(recipient.getId()).get(0);
        assertThat(asRecipient.participantId()).isEmpty();
        assertThat(asRecipient.participantName()).doesNotContain("Hidden");
    }

    @Test
    void banningAnAnonymousAuthorNeverRevealsWhoTheyAre() {
        User owner = user("Owner"), mod = user("Mod");
        var community = communities.create(owner.getId(), new CreateCommunityRequest("Anon Friendly", null, null, true));
        communities.join(community.slug(), mod.getId());
        communities.setModerator(community.slug(), owner.getId(), mod.getId(), true);
        PostResponse ownersAnonPost = posts.create(owner.getId(), request("ask", "Secretly the owner", null, community.id(), true));

        assertThatThrownBy(() -> communities.banPostAuthor(community.slug(), mod.getId(), UUID.fromString(ownersAnonPost.id())))
                .isInstanceOf(BadRequestException.class)
                .hasMessageNotContaining("owner")
                .hasMessageContaining("can't be banned from here");
    }
}
