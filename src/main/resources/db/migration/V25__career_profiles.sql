-- Career profile (G18-G21, see API-CHANGES.md): the opt-in career layer on top of a person's
-- Arena identity. Compensation is PRIVATE by default. Additive only.
--
-- Rollback:
--   DROP TABLE IF EXISTS public.arena_career_locations;
--   DROP TABLE IF EXISTS public.arena_career_profiles;

CREATE TABLE IF NOT EXISTS public.arena_career_profiles (
    id                       UUID PRIMARY KEY,
    created_at               TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at               TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    demo_content             BOOLEAN NOT NULL DEFAULT false,
    user_id                  UUID NOT NULL REFERENCES public.arena_users (id),
    intent                   VARCHAR(20) NOT NULL,
    desired_role             VARCHAR(100),
    experience_level         VARCHAR(20),
    work_mode                VARCHAR(20) NOT NULL DEFAULT 'ANY',
    notice_period            VARCHAR(20),
    compensation_visibility  VARCHAR(20) NOT NULL DEFAULT 'PRIVATE',
    expected_min             INT,
    expected_max             INT,
    open_to_work             BOOLEAN NOT NULL DEFAULT false,
    published_at             TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT uk_career_profiles_user UNIQUE (user_id),
    CONSTRAINT ck_career_compensation CHECK (compensation_visibility IN ('PRIVATE', 'ON_APPLICATION', 'EMPLOYERS'))
);

CREATE TABLE IF NOT EXISTS public.arena_career_locations (
    career_id  UUID NOT NULL REFERENCES public.arena_career_profiles (id),
    location   VARCHAR(60) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_career_locations_career ON public.arena_career_locations (career_id);
