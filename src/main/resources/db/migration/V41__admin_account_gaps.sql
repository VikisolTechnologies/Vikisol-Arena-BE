-- FE-API-GAPS rows 42-54 (admin and account, B+). Additive only.
--
-- arena_users:
--   suspended_at / suspended_until / suspension_reason: an admin suspension (row 51, row 50).
--     suspended_until null = until an admin restores it.
--   banned_at: a permanent ban (row 50); only an admin restore lifts it.
--   sessions_revoked_at: force sign-out (row 51) - access tokens issued before it stop working.
--   last_active_at: last sign-in or token refresh (row 49 staff list).
--   last_data_export_at: when the person last downloaded their data (row 51 flags).
-- arena_user_active_days: one row per person per day they signed in or refreshed a session,
--   for the launch D1/D7 return rates (row 42). Counts only; no content.
-- arena_staff_launch_areas: the launch areas a Vikisol staff account covers (row 49).
-- arena_notification_preferences.jenny / .marketing: row 52 toggles. Marketing is opt-in.
--
-- Rollback:
--   ALTER TABLE public.arena_users DROP COLUMN IF EXISTS suspended_at, DROP COLUMN IF EXISTS suspended_until,
--     DROP COLUMN IF EXISTS suspension_reason, DROP COLUMN IF EXISTS banned_at, DROP COLUMN IF EXISTS sessions_revoked_at,
--     DROP COLUMN IF EXISTS last_active_at, DROP COLUMN IF EXISTS last_data_export_at;
--   DROP TABLE IF EXISTS public.arena_user_active_days;
--   DROP TABLE IF EXISTS public.arena_staff_launch_areas;
--   ALTER TABLE public.arena_notification_preferences DROP COLUMN IF EXISTS jenny, DROP COLUMN IF EXISTS marketing;

ALTER TABLE public.arena_users
    ADD COLUMN IF NOT EXISTS suspended_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS suspended_until TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS suspension_reason VARCHAR(500),
    ADD COLUMN IF NOT EXISTS banned_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS sessions_revoked_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS last_active_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN IF NOT EXISTS last_data_export_at TIMESTAMP WITH TIME ZONE;

CREATE TABLE IF NOT EXISTS public.arena_user_active_days (
    user_id  UUID NOT NULL REFERENCES public.arena_users (id) ON DELETE CASCADE,
    day      DATE NOT NULL,
    PRIMARY KEY (user_id, day)
);
CREATE INDEX IF NOT EXISTS idx_user_active_days_day ON public.arena_user_active_days (day);

CREATE TABLE IF NOT EXISTS public.arena_staff_launch_areas (
    user_id  UUID NOT NULL REFERENCES public.arena_users (id) ON DELETE CASCADE,
    area     VARCHAR(80) NOT NULL,
    PRIMARY KEY (user_id, area)
);

ALTER TABLE public.arena_notification_preferences
    ADD COLUMN IF NOT EXISTS jenny BOOLEAN NOT NULL DEFAULT true,
    ADD COLUMN IF NOT EXISTS marketing BOOLEAN NOT NULL DEFAULT false;
