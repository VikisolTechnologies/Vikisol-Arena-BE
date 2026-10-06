-- Jobs & applications (G22-G26, see API-CHANGES.md): must-haves and nice-to-haves on a posting,
-- screening questions, the candidate's answers and must-have evidence, the recruiter's private
-- per-must-have assessment, and a HIRED stage. Additive only.
--
-- Rows hanging off an application cascade with it: withdrawing (DELETE /applications/{id}) still
-- deletes the application as it always has.
--
-- Rollback (in this order; HIRED rows must be moved to another stage first):
--   DROP TABLE IF EXISTS public.arena_application_evidence;
--   DROP TABLE IF EXISTS public.arena_application_answers;
--   DROP TABLE IF EXISTS public.arena_job_screening_questions;
--   DROP TABLE IF EXISTS public.arena_job_requirements;
--   and restore arena_applications_stage_check without 'HIRED' (see V1).

CREATE TABLE IF NOT EXISTS public.arena_job_requirements (
    id            UUID PRIMARY KEY,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    demo_content  BOOLEAN NOT NULL DEFAULT false,
    posting_id    UUID NOT NULL REFERENCES public.arena_job_postings (id),
    kind          VARCHAR(8) NOT NULL,
    position      INT NOT NULL,
    text          VARCHAR(120) NOT NULL,
    CONSTRAINT ck_job_requirements_kind CHECK (kind IN ('MUST', 'NICE'))
);
CREATE INDEX IF NOT EXISTS idx_job_requirements_posting ON public.arena_job_requirements (posting_id, kind, position);

CREATE TABLE IF NOT EXISTS public.arena_job_screening_questions (
    id            UUID PRIMARY KEY,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    demo_content  BOOLEAN NOT NULL DEFAULT false,
    posting_id    UUID NOT NULL REFERENCES public.arena_job_postings (id),
    position      INT NOT NULL,
    text          VARCHAR(200) NOT NULL,
    required      BOOLEAN NOT NULL DEFAULT true
);
CREATE INDEX IF NOT EXISTS idx_job_screening_posting ON public.arena_job_screening_questions (posting_id, position);

CREATE TABLE IF NOT EXISTS public.arena_application_answers (
    id              UUID PRIMARY KEY,
    created_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    demo_content    BOOLEAN NOT NULL DEFAULT false,
    application_id  UUID NOT NULL REFERENCES public.arena_applications (id) ON DELETE CASCADE,
    question_id     UUID NOT NULL REFERENCES public.arena_job_screening_questions (id),
    answer          VARCHAR(1000) NOT NULL,
    CONSTRAINT uk_application_answers UNIQUE (application_id, question_id)
);

CREATE TABLE IF NOT EXISTS public.arena_application_evidence (
    id                   UUID PRIMARY KEY,
    created_at           TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at           TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    demo_content         BOOLEAN NOT NULL DEFAULT false,
    application_id       UUID NOT NULL REFERENCES public.arena_applications (id) ON DELETE CASCADE,
    requirement_id       UUID NOT NULL REFERENCES public.arena_job_requirements (id),
    candidate_evidence   VARCHAR(300),
    assessment           VARCHAR(16),
    assessment_note      VARCHAR(500),
    assessed_by_user_id  UUID REFERENCES public.arena_users (id),
    assessed_at          TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT uk_application_evidence UNIQUE (application_id, requirement_id),
    CONSTRAINT ck_application_evidence_assessment CHECK (assessment IS NULL OR assessment IN ('MET', 'PARTLY', 'NOT_MET', 'UNCLEAR'))
);

-- HIRED closes the pipeline (Applied -> Screening -> Interview -> Offer -> Hired). Widened the
-- same guarded way V20 widened the post intent check.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'arena_applications_stage_check'
          AND conrelid = 'public.arena_applications'::regclass
    ) THEN
        ALTER TABLE public.arena_applications DROP CONSTRAINT arena_applications_stage_check;
        ALTER TABLE public.arena_applications ADD CONSTRAINT arena_applications_stage_check
            CHECK (stage IN ('APPLIED', 'SCREENING', 'INTERVIEW', 'OFFER', 'HIRED', 'REJECTED'));
    END IF;
END $$;
