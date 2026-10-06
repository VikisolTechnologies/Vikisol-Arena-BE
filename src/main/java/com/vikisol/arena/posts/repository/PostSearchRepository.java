package com.vikisol.arena.posts.repository;

import com.vikisol.arena.posts.entity.PostAudience;
import com.vikisol.arena.posts.entity.PostIntentType;
import com.vikisol.arena.posts.entity.PostStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

// PERFORMANCE.md: search's candidate rows in one plain SQL read. The newest live posts of these
// kinds containing `pattern` ('%word%', lowercase) in their title, body or place, or in the
// author's or company's name when the post isn't anonymous - a superset of what SearchText can
// match - found through the V40 trigram indexes. Plain JDBC because a window can be thousands of
// rows of text, and nothing here needs an entity.
@Repository
@RequiredArgsConstructor
public class PostSearchRepository {

    private static final String SQL = """
            select p.id, p.intent_type, p.audience, p.anonymous, p.link_only, u.id as author_id, u.name as author_name,
                   e.company_name, p.title, p.body, p.location_text
            from arena_posts p
            join arena_users u on u.id = p.author_user_id
            left join arena_enterprise_profiles e on e.id = p.author_company_id
            where p.status in (:statuses) and p.intent_type in (:types)
              and u.deleted_at is null and u.banned_at is null
              and p.id in (
                select t.id from arena_posts t
                where lower(coalesce(t.title, '') || ' ' || t.body || ' ' || coalesce(t.location_text, '')) like :pattern
                union
                select a.id from arena_posts a join arena_users au on au.id = a.author_user_id
                where a.anonymous = false and lower(au.name) like :pattern
                union
                select c.id from arena_posts c join arena_enterprise_profiles ce on ce.id = c.author_company_id
                where c.anonymous = false and lower(ce.company_name) like :pattern)
            order by p.created_at desc
            limit :limit
            """;

    private final NamedParameterJdbcTemplate jdbc;

    public List<PostSearchRow> matching(Collection<PostStatus> statuses, Collection<PostIntentType> types, String pattern, int limit) {
        MapSqlParameterSource params = new MapSqlParameterSource()
                .addValue("statuses", statuses.stream().map(Enum::name).toList())
                .addValue("types", types.stream().map(Enum::name).toList())
                .addValue("pattern", pattern)
                .addValue("limit", limit);
        return jdbc.query(SQL, params, (rs, i) -> new PostSearchRow(
                rs.getObject("id", UUID.class), PostIntentType.valueOf(rs.getString("intent_type")),
                PostAudience.valueOf(rs.getString("audience")), rs.getBoolean("anonymous"), rs.getBoolean("link_only"),
                rs.getObject("author_id", UUID.class), rs.getString("author_name"), rs.getString("company_name"),
                rs.getString("title"), rs.getString("body"), rs.getString("location_text")));
    }
}
