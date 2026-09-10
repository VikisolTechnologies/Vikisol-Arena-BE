-- Backs the real /agent chat with server-side persistence, replacing the client-only ChatMessage[]
-- React state the page used to keep (lost on refresh/device change) alongside its buildReply()
-- keyword matcher, which was never a real AI and has been removed. See AgentService/
-- AgentServiceClient - the current binding (NoopAgentServiceClient) is honest about there being no
-- real agent backend yet; this table exists so a real one can be wired in later without another
-- migration.

CREATE TABLE public.arena_agent_conversations (
    id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    user_id UUID NOT NULL,
    title VARCHAR(255),
    CONSTRAINT arena_agent_conversations_pkey PRIMARY KEY (id),
    CONSTRAINT fk_agent_conversations_user FOREIGN KEY (user_id) REFERENCES public.arena_users(id)
);

CREATE INDEX idx_agent_conversations_user ON public.arena_agent_conversations(user_id, updated_at DESC);

CREATE TABLE public.arena_agent_messages (
    id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL,
    conversation_id UUID NOT NULL,
    role VARCHAR(20) NOT NULL,
    content TEXT NOT NULL,
    service_unavailable BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT arena_agent_messages_pkey PRIMARY KEY (id),
    CONSTRAINT fk_agent_messages_conversation FOREIGN KEY (conversation_id) REFERENCES public.arena_agent_conversations(id)
);

CREATE INDEX idx_agent_messages_conversation ON public.arena_agent_messages(conversation_id, created_at ASC);
