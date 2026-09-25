-- Arena restructure Phase 3 (Jenny): actions Jenny proposes (post, join, bid, apply, start a
-- project) that only run once the user approves them. JennySol holds the executable pending
-- action (external_action_id); Arena keeps what the user was shown and what happened.
CREATE TABLE public.arena_agent_actions (
    id                  UUID PRIMARY KEY,
    created_at          TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at          TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    demo_content        BOOLEAN NOT NULL DEFAULT false,
    message_id          UUID NOT NULL REFERENCES public.arena_agent_messages (id) ON DELETE CASCADE,
    external_action_id  VARCHAR(100) NOT NULL,
    tool_name           VARCHAR(80) NOT NULL,
    args_json           TEXT NOT NULL,
    status              VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    result_json         TEXT,
    error               VARCHAR(500),
    CONSTRAINT uk_agent_actions_external UNIQUE (external_action_id),
    CONSTRAINT ck_agent_actions_status CHECK (status IN ('PENDING', 'DONE', 'DECLINED', 'FAILED', 'EXPIRED'))
);
CREATE INDEX idx_agent_actions_message_id ON public.arena_agent_actions (message_id);
