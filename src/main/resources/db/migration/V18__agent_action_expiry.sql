ALTER TABLE public.arena_agent_actions ADD COLUMN expires_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE public.arena_agent_actions DROP CONSTRAINT ck_agent_actions_status;
ALTER TABLE public.arena_agent_actions ADD CONSTRAINT ck_agent_actions_status
  CHECK (status IN ('PENDING', 'DONE', 'DECLINED', 'FAILED', 'EXPIRED', 'UNKNOWN'));
