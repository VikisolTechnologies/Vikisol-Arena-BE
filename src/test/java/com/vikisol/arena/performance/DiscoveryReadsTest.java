package com.vikisol.arena.performance;

import com.vikisol.arena.auth.entity.Role;
import com.vikisol.arena.auth.entity.User;
import com.vikisol.arena.auth.repository.UserRepository;
import com.vikisol.arena.common.geo.GeohashUtil;
import com.vikisol.arena.posts.dto.PostResponse;
import com.vikisol.arena.posts.entity.Post;
import com.vikisol.arena.posts.entity.PostIntentType;
import com.vikisol.arena.posts.entity.PostVisibility;
import com.vikisol.arena.posts.repository.PostRepository;
import com.vikisol.arena.posts.service.PostService;
import com.vikisol.arena.schema.EmbeddedPostgresAppTest;
import com.vikisol.arena.search.SearchService;
import com.vikisol.arena.search.SearchText;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/** PERFORMANCE.md: the faster nearby and search reads return what the slower ones did, or more. */
class DiscoveryReadsTest extends EmbeddedPostgresAppTest {

    @Autowired private UserRepository users;
    @Autowired private PostRepository posts;
    @Autowired private PostService postService;
    @Autowired private SearchService searchService;
    @Autowired private EntityManager em;

    private User user(String name) {
        return users.save(User.builder().email(name.toLowerCase() + "-" + UUID.randomUUID() + "@test.local")
                .passwordHash("x").name(name).role(Role.TALENT).build());
    }

    private Post at(User author, PostIntentType type, String body, double lat, double lng) {
        String geohash = GeohashUtil.encode(lat, lng);
        double[] approx = GeohashUtil.decode(geohash);
        return Post.builder().authorUser(author).intentType(type).body(body).visibility(PostVisibility.PUBLIC)
                .geohash(geohash).approxLat(approx[0]).approxLng(approx[1]).build();
    }

    @Test
    void nearbyFindsAnOlderPostBehindFiveHundredNewerOnes() {
        User host = user("Host"), viewer = user("Viewer");
        Post old = posts.save(at(host, PostIntentType.ACTIVITY, "Old Sunday cricket", 17.4401, 78.3489));
        em.flush();
        em.createNativeQuery("update arena_posts set created_at = now() - interval '60 days' where id = :id")
                .setParameter("id", old.getId()).executeUpdate();
        // The old read took the newest 500 open posts anywhere and filtered those by distance.
        List<Post> elsewhere = new ArrayList<>();
        for (int i = 0; i < 520; i++) elsewhere.add(at(host, PostIntentType.ACTIVITY, "Mumbai run " + i, 19.07, 72.88));
        posts.saveAll(elsewhere);
        em.flush();
        em.clear();

        List<PostResponse> nearby = postService.getNearby(viewer.getId(), 17.44, 78.35, 2, null, null);
        assertThat(nearby).extracting(PostResponse::id).containsExactly(old.getId().toString());
        assertThat(postService.getNearby(viewer.getId(), 19.07, 72.88, 2, null, null)).hasSize(520);
    }

    @Test
    void nearbyStillLeavesOutFarAndNonJoinablePosts() {
        User host = user("Host"), viewer = user("Viewer");
        Post inside = posts.save(at(host, PostIntentType.ACTIVITY, "Inside", 17.4401, 78.3489));
        posts.save(at(host, PostIntentType.ACTIVITY, "Ten km away", 17.53, 78.3489));
        posts.save(at(host, PostIntentType.UPDATE, "An update, not joinable", 17.4401, 78.3489));
        em.flush();
        em.clear();

        assertThat(postService.getNearby(viewer.getId(), 17.44, 78.35, 3, null, null))
                .extracting(PostResponse::id).containsExactly(inside.getId().toString());
    }

    @Test
    void searchAllMatchesSearchingEachKindOnItsOwn() {
        User author = user("Asha Rao"), viewer = user("Viewer");
        for (int i = 0; i < 12; i++) {
            posts.save(at(author, PostIntentType.ACTIVITY, "Weekend chess meetup " + i, 17.44, 78.35));
            posts.save(at(author, i % 2 == 0 ? PostIntentType.ASK : PostIntentType.UPDATE, "Anyone for chess " + i, 17.44, 78.35));
        }
        Post offer = at(author, PostIntentType.OFFER, "Teaching openings on weekends", 17.44, 78.35);
        offer.setTitle("Chess coaching");
        posts.save(offer);
        em.flush();
        em.clear();

        var all = searchService.search("chess", "all", 5, viewer.getId(), null, null, null);
        var activities = searchService.search("chess", "activities", 5, viewer.getId(), null, null, null);
        var discussions = searchService.search("chess", "discussions", 5, viewer.getId(), null, null, null);
        assertThat(all.activities()).hasSize(5).extracting(PostResponse::id)
                .containsExactlyElementsOf(activities.activities().stream().map(PostResponse::id).toList());
        assertThat(all.discussions()).hasSize(5).extracting(PostResponse::id)
                .containsExactlyElementsOf(discussions.discussions().stream().map(PostResponse::id).toList());
        // A title hit ranks first among discussions.
        assertThat(all.discussions().get(0).title()).isEqualTo("Chess coaching");
        // An author is found by name, through the same light rows.
        assertThat(postService.search(viewer.getId(), SearchText.terms("asha chess"), List.of(PostIntentType.ACTIVITY), 50))
                .hasSize(12);
    }
}
