package com.vikisol.arena.schema;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/** V21 runs on a real Postgres and leaves every index it promises in place. */
class MissingIndexMigrationTest extends EmbeddedPostgresAppTest {

    @Autowired JdbcTemplate jdbc;

    @Test
    void v21IndexesExist() {
        List<String> indexes = jdbc.queryForList(
                "select indexname from pg_indexes where schemaname = 'public'", String.class);
        assertThat(indexes).contains(
                "idx_posts_author_company_created",
                "idx_post_comments_author_created",
                "idx_post_reactions_user_id",
                "idx_room_messages_sender_user_id",
                "idx_thread_messages_sender_user_id",
                "idx_communities_created_by_user_id");
    }
}
