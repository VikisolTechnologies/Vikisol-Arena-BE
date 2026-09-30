-- Architect decisions, 30 Sep 2026 (DECISIONS.md).
--
-- 1. arena_job_postings.closed_at: when a posting was last closed. The candidate-retention job
--    (CandidateRetentionService) counts 12 months from it. Postings already closed get their
--    last update time, the closest record there is of when they closed.
-- 2. arena_enterprise_profiles.verification_grandfathered_at: set on every company not yet
--    verified at the moment company_verification_required is switched on. Those companies keep
--    publishing ("verified-legacy") until an admin reviews them; companies created after that
--    must verify.
--
-- Rollback:
--   ALTER TABLE public.arena_job_postings DROP COLUMN IF EXISTS closed_at;
--   ALTER TABLE public.arena_enterprise_profiles DROP COLUMN IF EXISTS verification_grandfathered_at;

ALTER TABLE public.arena_job_postings ADD COLUMN IF NOT EXISTS closed_at TIMESTAMP WITH TIME ZONE;
UPDATE public.arena_job_postings SET closed_at = updated_at WHERE status = 'CLOSED' AND closed_at IS NULL;
CREATE INDEX IF NOT EXISTS idx_job_postings_closed_at ON public.arena_job_postings (closed_at) WHERE closed_at IS NOT NULL;

ALTER TABLE public.arena_enterprise_profiles ADD COLUMN IF NOT EXISTS verification_grandfathered_at TIMESTAMP WITH TIME ZONE;
