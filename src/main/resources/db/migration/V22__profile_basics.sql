-- FE-API-GAPS 1-5 (onboarding): why-you're-here intents, personal interests, availability and a
-- profile photo. Additive only.
--
-- Rollback:
--   DROP TABLE IF EXISTS public.arena_candidate_intents;
--   DROP TABLE IF EXISTS public.arena_candidate_interests;
--   DROP TABLE IF EXISTS public.arena_candidate_availability;
--   ALTER TABLE public.arena_candidate_profiles DROP COLUMN IF EXISTS photo_url;

ALTER TABLE public.arena_candidate_profiles ADD COLUMN IF NOT EXISTS photo_url varchar(1024);

CREATE TABLE IF NOT EXISTS public.arena_candidate_intents (
    candidate_id uuid NOT NULL REFERENCES public.arena_candidate_profiles (id),
    intent varchar(20) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_candidate_intents_candidate_id ON public.arena_candidate_intents (candidate_id);

CREATE TABLE IF NOT EXISTS public.arena_candidate_interests (
    candidate_id uuid NOT NULL REFERENCES public.arena_candidate_profiles (id),
    interest varchar(30) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_candidate_interests_candidate_id ON public.arena_candidate_interests (candidate_id);

CREATE TABLE IF NOT EXISTS public.arena_candidate_availability (
    candidate_id uuid NOT NULL REFERENCES public.arena_candidate_profiles (id),
    slot varchar(20) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_candidate_availability_candidate_id ON public.arena_candidate_availability (candidate_id);
