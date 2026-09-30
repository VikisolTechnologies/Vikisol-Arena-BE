-- Needs & offers (G14-G17, see API-CHANGES.md): a category and preferred time on a need or offer,
-- people's responses to it (an offer of help, or a request for what's offered), and the
-- two-sided completion that turns an accepted response into an outcome. Hangs off existing
-- ASK / OFFER posts; nothing existing is altered.
--
-- Rollback (drop in this order):
--   DROP TABLE IF EXISTS public.arena_need_completions;
--   DROP TABLE IF EXISTS public.arena_post_responses;
--   DROP TABLE IF EXISTS public.arena_need_details;

CREATE TABLE IF NOT EXISTS public.arena_need_details (
    id              UUID PRIMARY KEY,
    created_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    demo_content    BOOLEAN NOT NULL DEFAULT false,
    post_id         UUID NOT NULL REFERENCES public.arena_posts (id),
    category        VARCHAR(32) NOT NULL,
    preferred_time  VARCHAR(100),
    CONSTRAINT uk_need_details_post UNIQUE (post_id)
);

CREATE TABLE IF NOT EXISTS public.arena_post_responses (
    id               UUID PRIMARY KEY,
    created_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    demo_content     BOOLEAN NOT NULL DEFAULT false,
    post_id          UUID NOT NULL REFERENCES public.arena_posts (id),
    user_id          UUID NOT NULL REFERENCES public.arena_users (id),
    message          VARCHAR(500) NOT NULL,
    status           VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    decided_at       TIMESTAMP(6) WITH TIME ZONE,
    conversation_id  UUID REFERENCES public.arena_conversations (id),
    CONSTRAINT uk_post_responses_post_user UNIQUE (post_id, user_id),
    CONSTRAINT ck_post_responses_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED', 'WITHDRAWN'))
);
CREATE INDEX IF NOT EXISTS idx_post_responses_post ON public.arena_post_responses (post_id, created_at);
CREATE INDEX IF NOT EXISTS idx_post_responses_user ON public.arena_post_responses (user_id, created_at DESC);

CREATE TABLE IF NOT EXISTS public.arena_need_completions (
    id                    UUID PRIMARY KEY,
    created_at            TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at            TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    demo_content          BOOLEAN NOT NULL DEFAULT false,
    response_id           UUID NOT NULL REFERENCES public.arena_post_responses (id),
    owner_confirmed_at    TIMESTAMP(6) WITH TIME ZONE,
    responder_confirmed_at TIMESTAMP(6) WITH TIME ZONE,
    owner_note            VARCHAR(500),
    responder_note        VARCHAR(500),
    completed_at          TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT uk_need_completions_response UNIQUE (response_id)
);
CREATE INDEX IF NOT EXISTS idx_need_completions_completed ON public.arena_need_completions (completed_at) WHERE completed_at IS NOT NULL;
