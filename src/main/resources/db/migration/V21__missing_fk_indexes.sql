-- API hardening (29 Sep 2026): foreign keys that are queried, or that Postgres has to scan when the
-- referenced user/company row is deleted, but that no index covers as a leading column. Additive
-- only; nothing reads or writes differently.
--
-- Rollback (safe at any time, no data involved):
--   DROP INDEX IF EXISTS public.idx_posts_author_company_created;
--   DROP INDEX IF EXISTS public.idx_post_comments_author_created;
--   DROP INDEX IF EXISTS public.idx_post_reactions_user_id;
--   DROP INDEX IF EXISTS public.idx_room_messages_sender_user_id;
--   DROP INDEX IF EXISTS public.idx_thread_messages_sender_user_id;
--   DROP INDEX IF EXISTS public.idx_communities_created_by_user_id;

-- Company page posts (PostRepository.findByAuthorCompanyIdOrderByCreatedAtDesc) - a public,
-- guest-reachable list. Only company posts have the column set.
CREATE INDEX IF NOT EXISTS idx_posts_author_company_created
    ON public.arena_posts (author_company_id, created_at DESC) WHERE author_company_id IS NOT NULL;

-- The anonymous-comment rate limit (countByAuthorUserIdAndAnonymousTrueAndCreatedAtAfter) runs on
-- every anonymous reply.
CREATE INDEX IF NOT EXISTS idx_post_comments_author_created
    ON public.arena_post_comments (author_user_id, created_at);

-- "My vote" lookups filter by user first; the unique (post_id, user_id) index leads with post.
CREATE INDEX IF NOT EXISTS idx_post_reactions_user_id ON public.arena_post_reactions (user_id);

-- Message tables grow fastest. Without these, deleting a user (demo cleanup) scans every message
-- to check the foreign key.
CREATE INDEX IF NOT EXISTS idx_room_messages_sender_user_id ON public.arena_room_messages (sender_user_id);
CREATE INDEX IF NOT EXISTS idx_thread_messages_sender_user_id ON public.arena_thread_messages (sender_user_id);

-- CommunityService.create checks how many communities the caller already started.
CREATE INDEX IF NOT EXISTS idx_communities_created_by_user_id ON public.arena_communities (created_by_user_id);
