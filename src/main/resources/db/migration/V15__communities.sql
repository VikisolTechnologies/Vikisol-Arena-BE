-- Arena restructure Phase 2 (Discuss), part B: user-created communities.

CREATE TABLE public.arena_communities (
    id                  UUID PRIMARY KEY,
    created_at          TIMESTAMP(6) NOT NULL,
    updated_at          TIMESTAMP(6) NOT NULL,
    demo_content        BOOLEAN NOT NULL DEFAULT false,
    slug                VARCHAR(40) NOT NULL,
    name                VARCHAR(60) NOT NULL,
    description         VARCHAR(500),
    emoji               VARCHAR(16) NOT NULL,
    created_by_user_id  UUID NOT NULL REFERENCES public.arena_users (id),
    -- Whether members may post/reply anonymously here (Phase 2 part C); owners can turn it off.
    allow_anonymous     BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT uk_communities_slug UNIQUE (slug)
);

CREATE TABLE public.arena_community_members (
    id            UUID PRIMARY KEY,
    created_at    TIMESTAMP(6) NOT NULL,
    updated_at    TIMESTAMP(6) NOT NULL,
    demo_content  BOOLEAN NOT NULL DEFAULT false,
    community_id  UUID NOT NULL REFERENCES public.arena_communities (id),
    user_id       UUID NOT NULL REFERENCES public.arena_users (id),
    role          VARCHAR(16) NOT NULL,
    -- A banned member keeps their row (so leaving and re-joining can't lift the ban).
    banned        BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT uk_community_members_community_user UNIQUE (community_id, user_id),
    CONSTRAINT ck_community_members_role CHECK (role IN ('OWNER', 'MODERATOR', 'MEMBER'))
);
CREATE INDEX idx_community_members_user_id ON public.arena_community_members (user_id);

-- A discussion can belong to one community (NULL = general Discuss).
ALTER TABLE public.arena_posts ADD COLUMN community_id UUID REFERENCES public.arena_communities (id);
CREATE INDEX idx_posts_community_id ON public.arena_posts (community_id);
-- Set when a community owner/moderator removes a post (status goes to CLOSED at the same time,
-- which already hides it from every feed, search and list).
ALTER TABLE public.arena_posts ADD COLUMN removed_reason VARCHAR(200);
