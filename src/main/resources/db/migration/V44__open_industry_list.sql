-- FE-API-GAPS row 62: the industry list was a closed five (check constraints from V1). It is now
-- a table staff manage at /admin/industries. Profiles, companies and jobs keep storing the key,
-- which is now a foreign key to this table. Retired industries are deactivated, never deleted,
-- so existing profiles keep their value.
--
-- Rollback (only while every row still uses one of the original five):
--   ALTER TABLE public.arena_candidate_profiles DROP CONSTRAINT IF EXISTS fk_candidate_profiles_industry;
--   ALTER TABLE public.arena_enterprise_profiles DROP CONSTRAINT IF EXISTS fk_enterprise_profiles_industry;
--   ALTER TABLE public.arena_job_postings DROP CONSTRAINT IF EXISTS fk_job_postings_industry;
--   re-add the three V1 *_industry_check constraints; DROP TABLE public.arena_industries;

CREATE TABLE IF NOT EXISTS public.arena_industries (
    key        VARCHAR(64)  PRIMARY KEY,
    label      VARCHAR(80)  NOT NULL,
    active     BOOLEAN      NOT NULL DEFAULT TRUE,
    position   INTEGER      NOT NULL DEFAULT 0,
    created_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    updated_at TIMESTAMPTZ  NOT NULL DEFAULT now(),
    CONSTRAINT arena_industries_key_format CHECK (key ~ '^[A-Z0-9_]+$')
);
CREATE UNIQUE INDEX IF NOT EXISTS uq_industries_label ON public.arena_industries (lower(label));

INSERT INTO public.arena_industries (key, label, position) VALUES
    ('ENGINEERING', 'Engineering', 10),
    ('DESIGN', 'Design', 20),
    ('SALES', 'Sales', 30),
    ('HEALTHCARE', 'Healthcare', 40),
    ('LOGISTICS', 'Logistics', 50)
ON CONFLICT (key) DO NOTHING;

ALTER TABLE public.arena_candidate_profiles DROP CONSTRAINT IF EXISTS arena_candidate_profiles_industry_check;
ALTER TABLE public.arena_enterprise_profiles DROP CONSTRAINT IF EXISTS arena_enterprise_profiles_industry_check;
ALTER TABLE public.arena_job_postings DROP CONSTRAINT IF EXISTS arena_job_postings_industry_check;

-- ARCHITECT-REVIEW-BE-1 SHOULD-FIX (migrations): NOT VALID + a separate VALIDATE CONSTRAINT.
-- A plain ADD CONSTRAINT ... FOREIGN KEY takes an ACCESS EXCLUSIVE lock on both tables for as
-- long as it takes to scan and verify every existing row - on a production table with real
-- traffic, that's a write-blocking outage for the duration. NOT VALID adds the constraint
-- (enforced on every new/updated row from this moment on) without that scan; VALIDATE CONSTRAINT
-- then does the scan under a much weaker SHARE UPDATE EXCLUSIVE lock, which doesn't block reads
-- or writes. Same end state, no full-table lock window.
ALTER TABLE public.arena_candidate_profiles
    ADD CONSTRAINT fk_candidate_profiles_industry FOREIGN KEY (industry) REFERENCES public.arena_industries (key) NOT VALID;
ALTER TABLE public.arena_enterprise_profiles
    ADD CONSTRAINT fk_enterprise_profiles_industry FOREIGN KEY (industry) REFERENCES public.arena_industries (key) NOT VALID;
ALTER TABLE public.arena_job_postings
    ADD CONSTRAINT fk_job_postings_industry FOREIGN KEY (industry) REFERENCES public.arena_industries (key) NOT VALID;

ALTER TABLE public.arena_candidate_profiles VALIDATE CONSTRAINT fk_candidate_profiles_industry;
ALTER TABLE public.arena_enterprise_profiles VALIDATE CONSTRAINT fk_enterprise_profiles_industry;
ALTER TABLE public.arena_job_postings VALIDATE CONSTRAINT fk_job_postings_industry;
