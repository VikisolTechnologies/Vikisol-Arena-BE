-- Community projects (G29-G31, see API-CHANGES.md): a COLLAB post ("Start a project") that people
-- join for an open role; its Room is the team space. The paid marketplace (arena_projects: bids,
-- award, milestones) is untouched. Additive only.
--
-- Rollback (COLLAB posts must be removed or re-typed first):
--   DROP TABLE IF EXISTS public.arena_project_members;
--   DROP TABLE IF EXISTS public.arena_project_roles;
--   and restore arena_posts_intent_type_check without 'COLLAB' (see V20).

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'arena_posts_intent_type_check'
          AND conrelid = 'public.arena_posts'::regclass
    ) THEN
        ALTER TABLE public.arena_posts DROP CONSTRAINT arena_posts_intent_type_check;
        ALTER TABLE public.arena_posts ADD CONSTRAINT arena_posts_intent_type_check
            CHECK (intent_type IN ('ACTIVITY', 'ASK', 'UPDATE', 'COMPANY', 'OFFER', 'COLLAB'));
    END IF;
END $$;

CREATE TABLE IF NOT EXISTS public.arena_project_roles (
    id            UUID PRIMARY KEY,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    demo_content  BOOLEAN NOT NULL DEFAULT false,
    post_id       UUID NOT NULL REFERENCES public.arena_posts (id),
    position      INT NOT NULL,
    title         VARCHAR(80) NOT NULL,
    description   VARCHAR(300),
    slots         INT NOT NULL DEFAULT 1,
    CONSTRAINT ck_project_roles_slots CHECK (slots BETWEEN 1 AND 50)
);
CREATE INDEX IF NOT EXISTS idx_project_roles_post ON public.arena_project_roles (post_id, position);

-- Which role someone asked for when joining, and their note to the owner. The join itself (and
-- its approval) stays in arena_post_joins.
CREATE TABLE IF NOT EXISTS public.arena_project_members (
    id            UUID PRIMARY KEY,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    demo_content  BOOLEAN NOT NULL DEFAULT false,
    post_id       UUID NOT NULL REFERENCES public.arena_posts (id),
    user_id       UUID NOT NULL REFERENCES public.arena_users (id),
    role_id       UUID REFERENCES public.arena_project_roles (id),
    message       VARCHAR(500),
    CONSTRAINT uk_project_members_post_user UNIQUE (post_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_project_members_user ON public.arena_project_members (user_id);
