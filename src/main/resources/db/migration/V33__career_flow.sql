-- Career per ARENA-APP-FLOW §6 (FE-API-GAPS rows 19, 32). Additive columns on
-- arena_career_profiles: the setup's extra fields and a per-field visibility map. Pay stays
-- private and is shared only per application ("include my CTC", arena_applications.include_ctc).
--
-- Rollback:
--   ALTER TABLE public.arena_career_profiles DROP COLUMN IF EXISTS current_company,
--     DROP COLUMN IF EXISTS work_status, DROP COLUMN IF EXISTS last_working_day,
--     DROP COLUMN IF EXISTS experience_months, DROP COLUMN IF EXISTS role_family,
--     DROP COLUMN IF EXISTS current_ctc_fixed, DROP COLUMN IF EXISTS current_ctc_variable,
--     DROP COLUMN IF EXISTS negotiable, DROP COLUMN IF EXISTS details_json, DROP COLUMN IF EXISTS visibility_json;

ALTER TABLE public.arena_career_profiles ADD COLUMN IF NOT EXISTS current_company VARCHAR(80);
ALTER TABLE public.arena_career_profiles ADD COLUMN IF NOT EXISTS work_status VARCHAR(16);
ALTER TABLE public.arena_career_profiles ADD COLUMN IF NOT EXISTS last_working_day DATE;
ALTER TABLE public.arena_career_profiles ADD COLUMN IF NOT EXISTS experience_months INT;
ALTER TABLE public.arena_career_profiles ADD COLUMN IF NOT EXISTS role_family VARCHAR(20);
ALTER TABLE public.arena_career_profiles ADD COLUMN IF NOT EXISTS current_ctc_fixed INT;
ALTER TABLE public.arena_career_profiles ADD COLUMN IF NOT EXISTS current_ctc_variable INT;
ALTER TABLE public.arena_career_profiles ADD COLUMN IF NOT EXISTS negotiable BOOLEAN;
-- skills with proficiency, SAP modules, certifications, desired roles, work modes, relocate,
-- shift, company sizes, links, education, languages.
ALTER TABLE public.arena_career_profiles ADD COLUMN IF NOT EXISTS details_json TEXT NOT NULL DEFAULT '{}';
ALTER TABLE public.arena_career_profiles ADD COLUMN IF NOT EXISTS visibility_json TEXT NOT NULL DEFAULT '{}';

-- Talent search shows only published career profiles (flow §8).
CREATE INDEX IF NOT EXISTS idx_career_profiles_published ON public.arena_career_profiles (user_id) WHERE published_at IS NOT NULL;
