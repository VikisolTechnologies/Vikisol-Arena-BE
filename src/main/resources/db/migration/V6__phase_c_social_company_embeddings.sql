-- ARENA-V2-PRODUCT-ARCHITECTURE.md Phase C: comments/reactions, company-follow, embedding-based
-- feed ranking. Matches Hibernate's expected DDL exactly (ddl-auto: validate is active) - see
-- entities in posts/follows for the Java side of every column below.

-- posts: cached embedding (§7.3 feed ranking's "relevance" term) - a comma-joined TEXT vector,
-- not a `vector`/array column, see EmbeddingUtil's own comment for why.
ALTER TABLE public.arena_posts ADD COLUMN embedding TEXT;

-- post_comments: field-for-field continuation of the ThreadMessage -> RoomMessage lineage.
CREATE TABLE arena_post_comments (
    id               UUID PRIMARY KEY,
    created_at       TIMESTAMP(6) NOT NULL,
    updated_at       TIMESTAMP(6) NOT NULL,
    post_id          UUID NOT NULL REFERENCES arena_posts (id),
    author_user_id   UUID NOT NULL REFERENCES arena_users (id),
    content          TEXT NOT NULL
);
CREATE INDEX idx_post_comments_post_id ON public.arena_post_comments (post_id);

-- post_reactions: independent join entity, same shape as arena_post_joins/arena_follows.
CREATE TABLE arena_post_reactions (
    id               UUID PRIMARY KEY,
    created_at       TIMESTAMP(6) NOT NULL,
    updated_at       TIMESTAMP(6) NOT NULL,
    post_id          UUID NOT NULL REFERENCES arena_posts (id),
    user_id          UUID NOT NULL REFERENCES arena_users (id),
    CONSTRAINT uk_post_reactions_post_user UNIQUE (post_id, user_id)
);
CREATE INDEX idx_post_reactions_post_id ON public.arena_post_reactions (post_id);

-- follows: company-follow added additively (see DECISIONS.md's ModerationItem-generalization
-- precedent) - following_user_id becomes nullable (a COMPANY-target row has none),
-- following_company_id/target_type are the new nullable/discriminator counterparts. Existing
-- rows (all user-to-user follows so far) get 'USER' via the column default, correctly
-- preserving their current meaning.
ALTER TABLE public.arena_follows ALTER COLUMN following_user_id DROP NOT NULL;
ALTER TABLE public.arena_follows ADD COLUMN following_company_id UUID REFERENCES arena_enterprise_profiles (id);
ALTER TABLE public.arena_follows ADD COLUMN target_type VARCHAR(255) NOT NULL DEFAULT 'USER';
ALTER TABLE public.arena_follows ADD CONSTRAINT uk_follows_follower_company UNIQUE (follower_user_id, following_company_id);
CREATE INDEX idx_follows_following_company_id ON public.arena_follows (following_company_id) WHERE following_company_id IS NOT NULL;
