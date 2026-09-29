-- Company verification per ARENA-APP-FLOW §8 B2 / §9 (FE-API-GAPS row 29). Additive.
-- - The work-email code now proves the domain; an Arena admin then approves or rejects with a
--   reason (the verification queue). Statuses: PENDING (code sent or domain confirmed, waiting
--   for review), VERIFIED, REJECTED.
-- - The company workspace gains website, GSTIN / CIN (optional, checked by the admin), HQ city and
--   a logo image.
--
-- Rollback:
--   ALTER TABLE public.arena_enterprise_profiles DROP COLUMN IF EXISTS website, DROP COLUMN IF EXISTS gstin,
--     DROP COLUMN IF EXISTS cin, DROP COLUMN IF EXISTS hq_city, DROP COLUMN IF EXISTS logo_url;
--   ALTER TABLE public.arena_business_verifications DROP COLUMN IF EXISTS domain_confirmed_at,
--     DROP COLUMN IF EXISTS review_note, DROP COLUMN IF EXISTS reviewed_by_user_id, DROP COLUMN IF EXISTS reviewed_at;
--   and restore ck_business_verifications_status to ('PENDING', 'VERIFIED') once no row is REJECTED.

ALTER TABLE public.arena_enterprise_profiles ADD COLUMN IF NOT EXISTS website VARCHAR(255);
ALTER TABLE public.arena_enterprise_profiles ADD COLUMN IF NOT EXISTS gstin VARCHAR(15);
ALTER TABLE public.arena_enterprise_profiles ADD COLUMN IF NOT EXISTS cin VARCHAR(21);
ALTER TABLE public.arena_enterprise_profiles ADD COLUMN IF NOT EXISTS hq_city VARCHAR(60);
ALTER TABLE public.arena_enterprise_profiles ADD COLUMN IF NOT EXISTS logo_url VARCHAR(500);

ALTER TABLE public.arena_business_verifications ADD COLUMN IF NOT EXISTS domain_confirmed_at TIMESTAMP(6) WITH TIME ZONE;
ALTER TABLE public.arena_business_verifications ADD COLUMN IF NOT EXISTS review_note VARCHAR(500);
ALTER TABLE public.arena_business_verifications ADD COLUMN IF NOT EXISTS reviewed_by_user_id UUID REFERENCES public.arena_users (id);
ALTER TABLE public.arena_business_verifications ADD COLUMN IF NOT EXISTS reviewed_at TIMESTAMP(6) WITH TIME ZONE;
-- Rows verified by code alone (this branch only) keep their badge and count as domain-confirmed.
UPDATE public.arena_business_verifications SET domain_confirmed_at = verified_at WHERE status = 'VERIFIED' AND domain_confirmed_at IS NULL;

ALTER TABLE public.arena_business_verifications DROP CONSTRAINT IF EXISTS ck_business_verifications_status;
ALTER TABLE public.arena_business_verifications ADD CONSTRAINT ck_business_verifications_status
    CHECK (status IN ('PENDING', 'VERIFIED', 'REJECTED'));

CREATE INDEX IF NOT EXISTS idx_business_verifications_queue ON public.arena_business_verifications (status, domain_confirmed_at);
