-- FE-API-GAPS row 61: report a person (not a post, room or chat). The report points at the
-- account; if that account's row is ever deleted the report stays, unlinked.
--
-- Rollback:
--   DELETE FROM public.arena_moderation_items WHERE content_type = 'USER';
--   ALTER TABLE public.arena_moderation_items DROP COLUMN IF EXISTS reported_user_id;

ALTER TABLE public.arena_moderation_items
    ADD COLUMN IF NOT EXISTS reported_user_id UUID REFERENCES public.arena_users (id) ON DELETE SET NULL;
CREATE INDEX IF NOT EXISTS idx_moderation_items_reported_user ON public.arena_moderation_items (reported_user_id)
    WHERE reported_user_id IS NOT NULL;
