-- Founder decision (DECISIONS.md, 30 Sep 2026): Jenny always prepares and the person approves
-- each action. There is no autopilot: the `agent_autopilot` feature flag is deleted if an admin
-- ever created it, profiles set to AUTOPILOT go back to SUPERVISED (the default), and the
-- column no longer accepts AUTOPILOT.
--
-- Rollback (re-allows the value; the deleted flag row and the old per-profile values are not
-- restored, deliberately):
--   ALTER TABLE public.arena_candidate_profiles DROP CONSTRAINT IF EXISTS arena_candidate_profiles_autonomy_check;
--   ALTER TABLE public.arena_candidate_profiles ADD CONSTRAINT arena_candidate_profiles_autonomy_check
--     CHECK (autonomy IN ('MANUAL', 'SUPERVISED', 'AUTOPILOT'));

DELETE FROM public.arena_feature_flags WHERE lower(key) IN ('agent_autopilot', 'autopilot');

UPDATE public.arena_candidate_profiles SET autonomy = 'SUPERVISED' WHERE autonomy = 'AUTOPILOT';

ALTER TABLE public.arena_candidate_profiles DROP CONSTRAINT IF EXISTS arena_candidate_profiles_autonomy_check;
ALTER TABLE public.arena_candidate_profiles ADD CONSTRAINT arena_candidate_profiles_autonomy_check
    CHECK (autonomy IN ('MANUAL', 'SUPERVISED'));
