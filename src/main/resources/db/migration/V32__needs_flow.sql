-- Needs & offers per ARENA-APP-FLOW §4 (FE-API-GAPS rows 13, 27, 38). Additive columns on
-- arena_need_details. The category list follows the frontend's (src/lib/intake/schemas/need.ts);
-- the few rows written with the earlier list (this branch only) are mapped across.
--
-- Rollback:
--   ALTER TABLE public.arena_need_details DROP COLUMN IF EXISTS urgency, DROP COLUMN IF EXISTS help_type,
--     DROP COLUMN IF EXISTS answers_json, DROP COLUMN IF EXISTS offer_days_json,
--     DROP COLUMN IF EXISTS offer_limit, DROP COLUMN IF EXISTS proof_url;

ALTER TABLE public.arena_need_details ADD COLUMN IF NOT EXISTS urgency VARCHAR(16);
ALTER TABLE public.arena_need_details ADD COLUMN IF NOT EXISTS help_type VARCHAR(16);
ALTER TABLE public.arena_need_details ADD COLUMN IF NOT EXISTS answers_json TEXT NOT NULL DEFAULT '{}';
ALTER TABLE public.arena_need_details ADD COLUMN IF NOT EXISTS offer_days_json TEXT NOT NULL DEFAULT '[]';
ALTER TABLE public.arena_need_details ADD COLUMN IF NOT EXISTS offer_limit VARCHAR(32);
ALTER TABLE public.arena_need_details ADD COLUMN IF NOT EXISTS proof_url VARCHAR(500);

UPDATE public.arena_need_details SET category = 'TECH' WHERE category = 'TECH_HELP';
UPDATE public.arena_need_details SET category = 'PET_CARE' WHERE category = 'PETS';
UPDATE public.arena_need_details SET category = 'PLANT_CARE' WHERE category = 'GARDENING';
UPDATE public.arena_need_details SET category = 'ADVICE' WHERE category = 'CAREER_ADVICE';
UPDATE public.arena_need_details SET category = 'OTHER' WHERE category IN ('COOKING', 'CLEANING', 'CREATIVE');

-- Offer limits count accepted requests in a recent window (row 27).
CREATE INDEX IF NOT EXISTS idx_post_responses_post_decided ON public.arena_post_responses (post_id, status, decided_at);
