-- Jobs per ARENA-APP-FLOW §6/§8 (FE-API-GAPS rows 20, 22, 28, 33, 41). Additive.
-- - postings: work mode (on-site / hybrid / remote), experience level, deadline, and a DRAFT status;
-- - typed screening questions (text / yes-no / number / choice);
-- - saved jobs;
-- - interview feedback per must-have (the overall rating becomes optional; the column was
--   already nullable).
--
-- Rollback:
--   DROP TABLE IF EXISTS public.arena_saved_jobs;
--   ALTER TABLE public.arena_interviews DROP COLUMN IF EXISTS feedback_must_haves_json;
--   ALTER TABLE public.arena_job_screening_questions DROP COLUMN IF EXISTS type, DROP COLUMN IF EXISTS options_json;
--   ALTER TABLE public.arena_job_postings DROP COLUMN IF EXISTS work_mode, DROP COLUMN IF EXISTS experience_level,
--     DROP COLUMN IF EXISTS deadline;
--   and restore arena_job_postings_status_check without 'DRAFT' (see V1) once no row uses it.

ALTER TABLE public.arena_job_postings ADD COLUMN IF NOT EXISTS work_mode VARCHAR(10);
ALTER TABLE public.arena_job_postings ADD COLUMN IF NOT EXISTS experience_level VARCHAR(10);
ALTER TABLE public.arena_job_postings ADD COLUMN IF NOT EXISTS deadline DATE;
UPDATE public.arena_job_postings SET work_mode = CASE WHEN remote THEN 'REMOTE' ELSE 'ONSITE' END WHERE work_mode IS NULL;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'arena_job_postings_status_check'
          AND conrelid = 'public.arena_job_postings'::regclass
    ) THEN
        ALTER TABLE public.arena_job_postings DROP CONSTRAINT arena_job_postings_status_check;
        ALTER TABLE public.arena_job_postings ADD CONSTRAINT arena_job_postings_status_check
            CHECK (status IN ('DRAFT', 'OPEN', 'PAUSED', 'CLOSED'));
    END IF;
END $$;

ALTER TABLE public.arena_job_screening_questions ADD COLUMN IF NOT EXISTS type VARCHAR(8) NOT NULL DEFAULT 'TEXT';
ALTER TABLE public.arena_job_screening_questions ADD COLUMN IF NOT EXISTS options_json TEXT NOT NULL DEFAULT '[]';

CREATE TABLE IF NOT EXISTS public.arena_saved_jobs (
    id            UUID PRIMARY KEY,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    demo_content  BOOLEAN NOT NULL DEFAULT false,
    user_id       UUID NOT NULL REFERENCES public.arena_users (id),
    posting_id    UUID NOT NULL REFERENCES public.arena_job_postings (id) ON DELETE CASCADE,
    CONSTRAINT uk_saved_jobs UNIQUE (user_id, posting_id)
);
CREATE INDEX IF NOT EXISTS idx_saved_jobs_user ON public.arena_saved_jobs (user_id, created_at DESC);

ALTER TABLE public.arena_interviews ADD COLUMN IF NOT EXISTS feedback_must_haves_json TEXT;
