package com.vikisol.arena.posts.repository;

import com.vikisol.arena.posts.entity.Post;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

// PERFORMANCE.md: open posts inside a set of geohash cells, found through
// idx_posts_open_geohash_c (V38) - one index range per cell prefix, OR'ed (a bitmap OR in the
// plan), instead of reading the newest N posts and filtering them in Java.
@Repository
@RequiredArgsConstructor
public class PostGeoRepository {

    // Geohash characters are 0-9 and b-z; '~' sorts after all of them in byte order, so
    // [prefix, prefix + '~') is exactly "starts with prefix".
    private static final String AFTER_LAST_CHAR = "~";

    private final EntityManager entityManager;

    @SuppressWarnings("unchecked")
    public List<Post> findOpenInCells(List<String> prefixes, int limit) {
        // Joinable kinds only (Post.isJoinable), which is all nearby shows.
        StringBuilder sql = new StringBuilder("select p.* from arena_posts p where p.status = 'OPEN' and p.geohash is not null"
                + " and p.anonymous = false and p.intent_type in ('ACTIVITY', 'ASK', 'COLLAB')");
        if (!prefixes.isEmpty()) {
            sql.append(" and (");
            for (int i = 0; i < prefixes.size(); i++) {
                if (i > 0) sql.append(" or ");
                sql.append("(p.geohash collate \"C\" >= :lo").append(i).append(" and p.geohash collate \"C\" < :hi").append(i).append(')');
            }
            sql.append(')');
        }
        sql.append(" order by p.created_at desc limit :limit");
        Query query = entityManager.createNativeQuery(sql.toString(), Post.class);
        for (int i = 0; i < prefixes.size(); i++) {
            query.setParameter("lo" + i, prefixes.get(i));
            query.setParameter("hi" + i, prefixes.get(i) + AFTER_LAST_CHAR);
        }
        query.setParameter("limit", limit);
        List<Post> posts = query.getResultList();
        // Load the authors in one query; the native query leaves them as lazy references.
        List<java.util.UUID> authorIds = posts.stream().map(p -> p.getAuthorUser().getId()).distinct().toList();
        if (!authorIds.isEmpty()) {
            entityManager.createQuery("select u from User u where u.id in :ids").setParameter("ids", authorIds).getResultList();
        }
        return posts;
    }
}
