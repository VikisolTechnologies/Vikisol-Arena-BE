-- People-app gaps per ARENA-APP-FLOW (FE-API-GAPS rows 15, 16, 18, 34) and the admin disputes
-- queue (flow §9). Additive.
-- - reports carry evidence files;
-- - notifications get a category, a snooze, and per-category preferences;
-- - a profile visibility setting for people search;
-- - employer connect requests (reaching out asks the person first);
-- - an admin can uphold a disputed no-show (REJECTED) as well as accept the dispute.
--
-- Rollback:
--   DROP TABLE IF EXISTS public.arena_connect_requests;
--   DROP TABLE IF EXISTS public.arena_notification_preferences;
--   ALTER TABLE public.arena_notifications DROP COLUMN IF EXISTS category, DROP COLUMN IF EXISTS snoozed_until;
--   ALTER TABLE public.arena_moderation_items DROP COLUMN IF EXISTS evidence_json;
--   ALTER TABLE public.arena_candidate_profiles DROP COLUMN IF EXISTS profile_visibility;
--   ALTER TABLE public.arena_activity_attendance DROP COLUMN IF EXISTS dispute_resolution_note,
--     DROP COLUMN IF EXISTS dispute_resolved_by_user_id;
--   and restore ck_activity_attendance_dispute to ('NONE', 'OPEN', 'ACCEPTED') once no row is REJECTED.

ALTER TABLE public.arena_moderation_items ADD COLUMN IF NOT EXISTS evidence_json TEXT NOT NULL DEFAULT '[]';

ALTER TABLE public.arena_notifications ADD COLUMN IF NOT EXISTS category VARCHAR(16);
ALTER TABLE public.arena_notifications ADD COLUMN IF NOT EXISTS snoozed_until TIMESTAMP(6) WITH TIME ZONE;

CREATE TABLE IF NOT EXISTS public.arena_notification_preferences (
    id            UUID PRIMARY KEY,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    demo_content  BOOLEAN NOT NULL DEFAULT false,
    user_id       UUID NOT NULL REFERENCES public.arena_users (id),
    activity      BOOLEAN NOT NULL DEFAULT true,
    need          BOOLEAN NOT NULL DEFAULT true,
    job           BOOLEAN NOT NULL DEFAULT true,
    message       BOOLEAN NOT NULL DEFAULT true,
    CONSTRAINT uk_notification_preferences_user UNIQUE (user_id)
);

ALTER TABLE public.arena_candidate_profiles ADD COLUMN IF NOT EXISTS profile_visibility VARCHAR(10) NOT NULL DEFAULT 'EVERYONE';

CREATE TABLE IF NOT EXISTS public.arena_connect_requests (
    id                 UUID PRIMARY KEY,
    created_at         TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at         TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    demo_content       BOOLEAN NOT NULL DEFAULT false,
    tenant_id          UUID NOT NULL REFERENCES public.arena_enterprise_profiles (id),
    candidate_user_id  UUID NOT NULL REFERENCES public.arena_users (id),
    sender_user_id     UUID REFERENCES public.arena_users (id),
    job_id             UUID REFERENCES public.arena_job_postings (id) ON DELETE SET NULL,
    note               VARCHAR(300) NOT NULL,
    status             VARCHAR(10) NOT NULL DEFAULT 'PENDING',
    decided_at         TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT uk_connect_requests UNIQUE (tenant_id, candidate_user_id),
    CONSTRAINT ck_connect_requests_status CHECK (status IN ('PENDING', 'ACCEPTED', 'DECLINED'))
);
CREATE INDEX IF NOT EXISTS idx_connect_requests_candidate ON public.arena_connect_requests (candidate_user_id, created_at DESC);

ALTER TABLE public.arena_activity_attendance ADD COLUMN IF NOT EXISTS dispute_resolution_note VARCHAR(500);
ALTER TABLE public.arena_activity_attendance ADD COLUMN IF NOT EXISTS dispute_resolved_by_user_id UUID REFERENCES public.arena_users (id);
ALTER TABLE public.arena_activity_attendance DROP CONSTRAINT IF EXISTS ck_activity_attendance_dispute;
ALTER TABLE public.arena_activity_attendance ADD CONSTRAINT ck_activity_attendance_dispute
    CHECK (dispute_status IN ('NONE', 'OPEN', 'ACCEPTED', 'REJECTED'));
