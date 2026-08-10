-- ARENA-MASTER-ARCHITECTURE.md v3 rewrite, Step 3 (see DECISIONS.md "the feed unifies ... at
-- the API response level"). Four independent, additive changes needed for the unified `/feed`
-- and the PART 7.5/7.6 Home feed rebuild - none of them touch existing data shape.

-- 1. Post.title (PART 7.5 PostCard H2 / PART 7.6 composer) - nullable, every existing post is
--    simply title-less (exactly how it already rendered pre-v3).
ALTER TABLE public.arena_posts ADD COLUMN title VARCHAR(255);

-- 2. User.handle (PART 6/7.12 `/people/{handle}`) - unique, backfilled for every existing row
--    from their name (lowercased, non-alphanumeric stripped, deduplicated with a row-number
--    suffix), matching HandleGenerator.generate's own algorithm closely enough that a real
--    signup right after this migration is very unlikely to collide with a seeded/backfilled one.
--    New rows (real signups) always set it explicitly via HandleGenerator - see AuthService.
ALTER TABLE public.arena_users ADD COLUMN handle VARCHAR(255);
WITH slugged AS (
    SELECT id,
           NULLIF(regexp_replace(lower(name), '[^a-z0-9]+', '', 'g'), '') AS base,
           row_number() OVER (
               PARTITION BY NULLIF(regexp_replace(lower(name), '[^a-z0-9]+', '', 'g'), '')
               ORDER BY created_at
           ) AS rn
    FROM public.arena_users
)
UPDATE public.arena_users u
SET handle = CASE WHEN slugged.rn = 1 THEN COALESCE(slugged.base, 'user')
                   ELSE COALESCE(slugged.base, 'user') || slugged.rn::text END
FROM slugged
WHERE u.id = slugged.id;
ALTER TABLE public.arena_users ADD CONSTRAINT uk_users_handle UNIQUE (handle);

-- 3. Project.kind (PART 7.6 PROJECT/FREELANCE) - every existing row keeps its current real
--    behavior (PROJECT), FREELANCE is purely an opt-in label for new projects going forward.
ALTER TABLE public.arena_projects ADD COLUMN kind VARCHAR(255) NOT NULL DEFAULT 'PROJECT';

-- 4. arena_post_saves (PART 6 "SAVE POST|DELETE /posts/{id}/save GET /me/saved") - independent
--    join entity, same shape as arena_post_reactions/arena_follows.
CREATE TABLE arena_post_saves (
    id         UUID PRIMARY KEY,
    created_at TIMESTAMP(6) NOT NULL,
    updated_at TIMESTAMP(6) NOT NULL,
    post_id    UUID NOT NULL REFERENCES arena_posts (id),
    user_id    UUID NOT NULL REFERENCES arena_users (id),
    CONSTRAINT uk_post_saves_post_user UNIQUE (post_id, user_id)
);
CREATE INDEX idx_post_saves_user_id ON public.arena_post_saves (user_id);
