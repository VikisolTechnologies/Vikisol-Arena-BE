-- Host-recorded result of an approved join, after the activity has started.
-- Null means not recorded yet. Existing rows stay null.
ALTER TABLE public.arena_post_joins ADD COLUMN outcome VARCHAR(32);
