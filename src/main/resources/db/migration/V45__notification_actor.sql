-- MARATHON-BE-2 step 1b blocker fix: PersonalDataService.erase used to scrub a deleted person's
-- name out of OTHER people's notifications with `replace(body, :name, 'Deleted user') ... like
-- '%name%'` - a full-table substring match that corrupts unrelated notifications whenever the
-- deleted person's name is a substring of someone else's (e.g. "Ravi" inside "Ravi Kumar", or
-- just "An"). This column lets erase() scrub only the rows that are actually about that person.
--
-- Nullable and not backfilled: old rows that already embedded a name have no recorded actor and
-- are left alone (safer than guessing), per the architect's note.
--
-- Rollback (safe to re-run before any data depends on it):
--   ALTER TABLE public.arena_notifications DROP COLUMN IF EXISTS actor_user_id;

ALTER TABLE public.arena_notifications ADD COLUMN IF NOT EXISTS actor_user_id uuid REFERENCES public.arena_users (id);
CREATE INDEX IF NOT EXISTS idx_notifications_actor_user_id ON public.arena_notifications (actor_user_id);
