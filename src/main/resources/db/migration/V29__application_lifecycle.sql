-- Application lifecycle (architect item 2; FE-API-GAPS rows 20, 21, 30, 31): a WITHDRAWN stage
-- (withdrawing no longer deletes the application), apply extras (cover note, CTC sharing), the
-- candidate's choice to show a hire on their profile, a per-application event timeline and
-- team-private recruiter notes. Additive only.
--
-- Rollback (in this order; WITHDRAWN rows must be deleted or moved first):
--   DROP TABLE IF EXISTS public.arena_application_notes;
--   DROP TABLE IF EXISTS public.arena_application_events;
--   ALTER TABLE public.arena_applications DROP COLUMN IF EXISTS cover_note,
--     DROP COLUMN IF EXISTS include_ctc, DROP COLUMN IF EXISTS show_outcome;
--   and restore arena_applications_stage_check as in V26.

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'arena_applications_stage_check'
          AND conrelid = 'public.arena_applications'::regclass
    ) THEN
        ALTER TABLE public.arena_applications DROP CONSTRAINT arena_applications_stage_check;
        ALTER TABLE public.arena_applications ADD CONSTRAINT arena_applications_stage_check
            CHECK (stage IN ('APPLIED', 'SCREENING', 'INTERVIEW', 'OFFER', 'HIRED', 'WITHDRAWN', 'REJECTED'));
    END IF;
END $$;

ALTER TABLE public.arena_applications ADD COLUMN IF NOT EXISTS cover_note VARCHAR(2000);
ALTER TABLE public.arena_applications ADD COLUMN IF NOT EXISTS include_ctc BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_applications ADD COLUMN IF NOT EXISTS show_outcome BOOLEAN NOT NULL DEFAULT false;

CREATE TABLE IF NOT EXISTS public.arena_application_events (
    id              UUID PRIMARY KEY,
    created_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    demo_content    BOOLEAN NOT NULL DEFAULT false,
    application_id  UUID NOT NULL REFERENCES public.arena_applications (id) ON DELETE CASCADE,
    type            VARCHAR(24) NOT NULL,
    stage           VARCHAR(24),
    actor_user_id   UUID REFERENCES public.arena_users (id),
    message         VARCHAR(600)
);
CREATE INDEX IF NOT EXISTS idx_application_events_app ON public.arena_application_events (application_id, created_at);

CREATE TABLE IF NOT EXISTS public.arena_application_notes (
    id              UUID PRIMARY KEY,
    created_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    demo_content    BOOLEAN NOT NULL DEFAULT false,
    application_id  UUID NOT NULL REFERENCES public.arena_applications (id) ON DELETE CASCADE,
    author_user_id  UUID NOT NULL REFERENCES public.arena_users (id),
    text            VARCHAR(2000) NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_application_notes_app ON public.arena_application_notes (application_id, created_at DESC);
