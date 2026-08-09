-- ARENA-V2-PRODUCT-ARCHITECTURE.md Phase A: the "post -> room" primitive. Three new domains -
-- posts, rooms, follows - matching Hibernate's expected DDL for the new entities exactly, since
-- ddl-auto: validate is active (see application.yml) and startup fails loudly on any mismatch.

-- posts: public feed browse (status = OPEN, most-recent-first) is FeedRankingService's hottest
-- query; author_user_id serves "my posts".
CREATE TABLE arena_posts (
    id                UUID PRIMARY KEY,
    created_at        TIMESTAMP(6) NOT NULL,
    updated_at        TIMESTAMP(6) NOT NULL,
    author_user_id    UUID NOT NULL REFERENCES arena_users (id),
    author_company_id UUID,
    intent_type       VARCHAR(255) NOT NULL,
    body              TEXT NOT NULL,
    location_text     VARCHAR(255),
    audience          VARCHAR(255) NOT NULL,
    visibility        VARCHAR(255) NOT NULL,
    capacity          INTEGER,
    spots_filled      INTEGER NOT NULL,
    status            VARCHAR(255) NOT NULL,
    starts_at         TIMESTAMP(6),
    ends_at           TIMESTAMP(6)
);
CREATE INDEX idx_posts_status_created_at ON public.arena_posts (status, created_at DESC);
CREATE INDEX idx_posts_author_user_id ON public.arena_posts (author_user_id);

-- element-collection join tables (Post.tags / Post.mediaUrls) - same shape as arena_project_skills.
CREATE TABLE arena_post_tags (
    post_id UUID NOT NULL REFERENCES arena_posts (id),
    tag     VARCHAR(255)
);
CREATE INDEX idx_post_tags_post_id ON public.arena_post_tags (post_id);

CREATE TABLE arena_post_media (
    post_id UUID NOT NULL REFERENCES arena_posts (id),
    url     VARCHAR(255)
);
CREATE INDEX idx_post_media_post_id ON public.arena_post_media (post_id);

-- post joins: one row per (post, user) join/request, mirrors arena_bids' independent-entity
-- shape rather than a collection on Post. UNIQUE prevents duplicate join requests.
CREATE TABLE arena_post_joins (
    id         UUID PRIMARY KEY,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    post_id    UUID NOT NULL REFERENCES arena_posts (id),
    user_id    UUID NOT NULL REFERENCES arena_users (id),
    status     VARCHAR(255) NOT NULL,
    decided_at TIMESTAMP(6),
    CONSTRAINT uk_post_joins_post_user UNIQUE (post_id, user_id)
);
CREATE INDEX idx_post_joins_post_id ON public.arena_post_joins (post_id);
CREATE INDEX idx_post_joins_user_id ON public.arena_post_joins (user_id);

-- rooms: 1:1 with post, created lazily on the post's first approved join (RoomService), not at
-- post-creation time - UPDATE posts never get one.
CREATE TABLE arena_rooms (
    id         UUID PRIMARY KEY,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    post_id    UUID NOT NULL UNIQUE REFERENCES arena_posts (id)
);

-- room members: generalizes arena_conversations' lastReadAtA/B to N members, one row per
-- (room, user). UNIQUE prevents duplicate membership; per-request tenant-style lookups both
-- directions are common ("my rooms" by user_id, "room roster" by room_id).
CREATE TABLE arena_room_members (
    id           UUID PRIMARY KEY,
    created_at   TIMESTAMP(6) NOT NULL,
    updated_at   TIMESTAMP(6) NOT NULL,
    room_id      UUID NOT NULL REFERENCES arena_rooms (id),
    user_id      UUID NOT NULL REFERENCES arena_users (id),
    role         VARCHAR(255) NOT NULL,
    last_read_at TIMESTAMP(6),
    CONSTRAINT uk_room_members_room_user UNIQUE (room_id, user_id)
);
CREATE INDEX idx_room_members_room_id ON public.arena_room_members (room_id);
CREATE INDEX idx_room_members_user_id ON public.arena_room_members (user_id);

-- room messages: fetching a room's messages, ordered, is the hottest chat query - same shape as
-- idx_thread_messages_conversation_id.
CREATE TABLE arena_room_messages (
    id             UUID PRIMARY KEY,
    created_at     TIMESTAMP(6) NOT NULL,
    updated_at     TIMESTAMP(6) NOT NULL,
    room_id        UUID NOT NULL REFERENCES arena_rooms (id),
    sender_user_id UUID NOT NULL REFERENCES arena_users (id),
    content        TEXT NOT NULL
);
CREATE INDEX idx_room_messages_room_id_created_at ON public.arena_room_messages (room_id, created_at);

-- room reports: Phase A's safety-minimum guardrail (ARENA-V2-PRODUCT-ARCHITECTURE.md §4 - full
-- verification/jitter/moderation-queue wiring is Phase B). Durable record only, no queue reads
-- this yet.
CREATE TABLE arena_room_reports (
    id               UUID PRIMARY KEY,
    created_at       TIMESTAMP(6) NOT NULL,
    updated_at       TIMESTAMP(6) NOT NULL,
    room_id          UUID NOT NULL REFERENCES arena_rooms (id),
    reporter_user_id UUID NOT NULL REFERENCES arena_users (id),
    reason           TEXT NOT NULL
);
CREATE INDEX idx_room_reports_room_id ON public.arena_room_reports (room_id);

-- follows: many-to-many, mirrors arena_bids' independent-entity shape, not arena_memberships'
-- 1:1-unique-per-user shape. Both directions are queried (followers of X, who X follows).
CREATE TABLE arena_follows (
    id                 UUID PRIMARY KEY,
    created_at         TIMESTAMP(6) NOT NULL,
    updated_at         TIMESTAMP(6) NOT NULL,
    follower_user_id   UUID NOT NULL REFERENCES arena_users (id),
    following_user_id  UUID NOT NULL REFERENCES arena_users (id),
    CONSTRAINT uk_follows_follower_following UNIQUE (follower_user_id, following_user_id)
);
CREATE INDEX idx_follows_follower_user_id ON public.arena_follows (follower_user_id);
CREATE INDEX idx_follows_following_user_id ON public.arena_follows (following_user_id);
