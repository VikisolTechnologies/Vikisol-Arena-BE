-- Activities (G7-G13, see API-CHANGES.md): type-specific details and a cover, host questions and
-- the answers to them, a waitlist, check-in and host-recorded attendance with a 72h dispute, and
-- private feedback. Every table hangs off an existing ACTIVITY post (arena_posts); nothing
-- existing is altered.
--
-- Rollback (drop in this order):
--   DROP TABLE IF EXISTS public.arena_activity_feedback;
--   DROP TABLE IF EXISTS public.arena_activity_attendance;
--   DROP TABLE IF EXISTS public.arena_activity_waitlist;
--   DROP TABLE IF EXISTS public.arena_activity_answers;
--   DROP TABLE IF EXISTS public.arena_activity_questions;
--   DROP TABLE IF EXISTS public.arena_activity_details;

CREATE TABLE IF NOT EXISTS public.arena_activity_details (
    id                UUID PRIMARY KEY,
    created_at        TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at        TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    demo_content      BOOLEAN NOT NULL DEFAULT false,
    post_id           UUID NOT NULL REFERENCES public.arena_posts (id),
    kind              VARCHAR(20) NOT NULL,
    details_json      TEXT NOT NULL DEFAULT '{}',
    cover_url         VARCHAR(1024),
    waitlist_enabled  BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT uk_activity_details_post UNIQUE (post_id)
);

CREATE TABLE IF NOT EXISTS public.arena_activity_questions (
    id            UUID PRIMARY KEY,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    demo_content  BOOLEAN NOT NULL DEFAULT false,
    post_id       UUID NOT NULL REFERENCES public.arena_posts (id),
    position      INT NOT NULL,
    text          VARCHAR(200) NOT NULL,
    required      BOOLEAN NOT NULL DEFAULT true
);
CREATE INDEX IF NOT EXISTS idx_activity_questions_post ON public.arena_activity_questions (post_id, position);

-- Keyed by person, not by join request: someone on the waitlist answers before a request exists.
CREATE TABLE IF NOT EXISTS public.arena_activity_answers (
    id            UUID PRIMARY KEY,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    demo_content  BOOLEAN NOT NULL DEFAULT false,
    question_id   UUID NOT NULL REFERENCES public.arena_activity_questions (id),
    user_id       UUID NOT NULL REFERENCES public.arena_users (id),
    answer        VARCHAR(500) NOT NULL,
    CONSTRAINT uk_activity_answers_question_user UNIQUE (question_id, user_id)
);
CREATE INDEX IF NOT EXISTS idx_activity_answers_user ON public.arena_activity_answers (user_id);

CREATE TABLE IF NOT EXISTS public.arena_activity_waitlist (
    id            UUID PRIMARY KEY,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    demo_content  BOOLEAN NOT NULL DEFAULT false,
    post_id       UUID NOT NULL REFERENCES public.arena_posts (id),
    user_id       UUID NOT NULL REFERENCES public.arena_users (id),
    status        VARCHAR(16) NOT NULL DEFAULT 'WAITING',
    joined_at     TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    promoted_at   TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT uk_activity_waitlist_post_user UNIQUE (post_id, user_id),
    CONSTRAINT ck_activity_waitlist_status CHECK (status IN ('WAITING', 'PROMOTED', 'LEFT', 'SKIPPED'))
);
CREATE INDEX IF NOT EXISTS idx_activity_waitlist_queue ON public.arena_activity_waitlist (post_id, status, joined_at);
CREATE INDEX IF NOT EXISTS idx_activity_waitlist_user ON public.arena_activity_waitlist (user_id);

CREATE TABLE IF NOT EXISTS public.arena_activity_attendance (
    id                   UUID PRIMARY KEY,
    created_at           TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at           TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    demo_content         BOOLEAN NOT NULL DEFAULT false,
    join_id              UUID NOT NULL REFERENCES public.arena_post_joins (id),
    checked_in_at        TIMESTAMP(6) WITH TIME ZONE,
    outcome_recorded_at  TIMESTAMP(6) WITH TIME ZONE,
    dispute_status       VARCHAR(16) NOT NULL DEFAULT 'NONE',
    dispute_reason       VARCHAR(500),
    disputed_at          TIMESTAMP(6) WITH TIME ZONE,
    dispute_resolved_at  TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT uk_activity_attendance_join UNIQUE (join_id),
    CONSTRAINT ck_activity_attendance_dispute CHECK (dispute_status IN ('NONE', 'OPEN', 'ACCEPTED'))
);

CREATE TABLE IF NOT EXISTS public.arena_activity_feedback (
    id            UUID PRIMARY KEY,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    demo_content  BOOLEAN NOT NULL DEFAULT false,
    post_id       UUID NOT NULL REFERENCES public.arena_posts (id),
    from_user_id  UUID NOT NULL REFERENCES public.arena_users (id),
    to_user_id    UUID NOT NULL REFERENCES public.arena_users (id),
    text          VARCHAR(500) NOT NULL,
    CONSTRAINT uk_activity_feedback_pair UNIQUE (post_id, from_user_id, to_user_id)
);
CREATE INDEX IF NOT EXISTS idx_activity_feedback_to ON public.arena_activity_feedback (to_user_id, created_at DESC);
