-- Arena restructure Phase 2 (Discuss), part C: anonymous posting (Discuss only) and anonymous chats.
-- The real author/participant is always stored - anonymity is a display rule enforced by the API,
-- so moderation (platform admins, reports) can still act on the real account.

ALTER TABLE public.arena_posts ADD COLUMN anonymous BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_post_comments ADD COLUMN anonymous BOOLEAN NOT NULL DEFAULT false;

-- Each side of a conversation can be hidden from the other. A conversation with either side hidden
-- is kept apart from the pair's normal DM (see ConversationRepository.findBetween).
ALTER TABLE public.arena_conversations ADD COLUMN anonymous_a BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_conversations ADD COLUMN anonymous_b BOOLEAN NOT NULL DEFAULT false;
-- The post an anonymous chat started from ("message the author"), if any.
ALTER TABLE public.arena_conversations ADD COLUMN post_id UUID REFERENCES public.arena_posts (id) ON DELETE SET NULL;
-- Closing an anonymous chat stops it for both sides; closed_by_user_id also stops the other person
-- opening a new anonymous chat with whoever closed it.
ALTER TABLE public.arena_conversations ADD COLUMN closed_by_user_id UUID REFERENCES public.arena_users (id);
ALTER TABLE public.arena_conversations ADD COLUMN closed_at TIMESTAMP(6) WITH TIME ZONE;

-- Reports on a conversation (anonymous chats' abuse path) go to the same platform moderation queue.
ALTER TABLE public.arena_moderation_items ADD COLUMN conversation_id UUID REFERENCES public.arena_conversations (id) ON DELETE CASCADE;
