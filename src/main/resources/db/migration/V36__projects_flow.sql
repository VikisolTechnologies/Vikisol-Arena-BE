-- Community projects per ARENA-APP-FLOW §7 (FE-API-GAPS row 26). Additive.
-- - project details: goal category, local / remote / both, duration in weeks, cover, and the
--   completion outcome;
-- - each role's helpful skills and weekly hours;
-- - a milestone checklist for the team room's Plan tab;
-- - the contributors named on completion (shown on their profiles).
--
-- Rollback:
--   DROP TABLE IF EXISTS public.arena_project_contributors;
--   DROP TABLE IF EXISTS public.arena_project_milestones;
--   DROP TABLE IF EXISTS public.arena_project_details;
--   ALTER TABLE public.arena_project_roles DROP COLUMN IF EXISTS skills_json, DROP COLUMN IF EXISTS hours_per_week;

CREATE TABLE IF NOT EXISTS public.arena_project_details (
    id            UUID PRIMARY KEY,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    demo_content  BOOLEAN NOT NULL DEFAULT false,
    post_id       UUID NOT NULL REFERENCES public.arena_posts (id),
    category      VARCHAR(20),
    where_mode    VARCHAR(10),
    weeks         INT,
    cover_url     VARCHAR(500),
    outcome       VARCHAR(1000),
    completed_at  TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT uk_project_details_post UNIQUE (post_id),
    CONSTRAINT ck_project_details_weeks CHECK (weeks IS NULL OR weeks BETWEEN 1 AND 52)
);

ALTER TABLE public.arena_project_roles ADD COLUMN IF NOT EXISTS skills_json TEXT NOT NULL DEFAULT '[]';
ALTER TABLE public.arena_project_roles ADD COLUMN IF NOT EXISTS hours_per_week INT;

CREATE TABLE IF NOT EXISTS public.arena_project_milestones (
    id                  UUID PRIMARY KEY,
    created_at          TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    demo_content        BOOLEAN NOT NULL DEFAULT false,
    post_id             UUID NOT NULL REFERENCES public.arena_posts (id),
    position            INT NOT NULL,
    title               VARCHAR(120) NOT NULL,
    done                BOOLEAN NOT NULL DEFAULT false,
    done_at             TIMESTAMP(6) WITH TIME ZONE,
    created_by_user_id  UUID REFERENCES public.arena_users (id)
);
CREATE INDEX IF NOT EXISTS idx_project_milestones_post ON public.arena_project_milestones (post_id, position);

CREATE TABLE IF NOT EXISTS public.arena_project_contributors (
    id            UUID PRIMARY KEY,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    demo_content  BOOLEAN NOT NULL DEFAULT false,
    post_id       UUID NOT NULL REFERENCES public.arena_posts (id),
    user_id       UUID NOT NULL REFERENCES public.arena_users (id),
    CONSTRAINT uk_project_contributors UNIQUE (post_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_project_contributors_user ON public.arena_project_contributors (user_id);
