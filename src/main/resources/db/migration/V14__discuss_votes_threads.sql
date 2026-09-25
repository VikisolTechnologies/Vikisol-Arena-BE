-- Arena restructure Phase 2 (Discuss), part A: up/down votes and threaded replies.

-- Votes reuse the existing one-row-per-(post, user) reaction table: every existing reaction was
-- a "like", so it becomes an upvote (value 1) via the default; a downvote is value -1. A post's
-- score is SUM(value); reactionCount stays "number of upvotes" for existing clients.
ALTER TABLE public.arena_post_reactions ADD COLUMN value SMALLINT NOT NULL DEFAULT 1;
ALTER TABLE public.arena_post_reactions ADD CONSTRAINT ck_post_reactions_value CHECK (value IN (-1, 1));

-- Threaded replies: a reply points at the comment it answers (NULL = top-level). A comment that
-- still has replies is soft-deleted (content blanked, shown as "[deleted]") so the thread under
-- it survives; one with no replies is still removed outright.
ALTER TABLE public.arena_post_comments ADD COLUMN parent_comment_id UUID REFERENCES public.arena_post_comments (id);
ALTER TABLE public.arena_post_comments ADD COLUMN deleted BOOLEAN NOT NULL DEFAULT false;
CREATE INDEX idx_post_comments_parent_comment_id ON public.arena_post_comments (parent_comment_id);
