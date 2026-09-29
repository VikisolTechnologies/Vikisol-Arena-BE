-- Activities per the flow doc (ARENA-APP-FLOW §3; FE-API-GAPS rows 7, 8, 10, 23, 25). Additive:
-- - activity details gain the flow's structure (category/subtype, level, cost, type answers, what
--   to bring, accessibility, indoor, minimum size, repeat, women-only label, reach). V23's `kind`
--   and `details_json` columns are no longer written; `kind` becomes nullable.
-- - a join request can carry the joiner's note and the host's decision note.
-- - posts carry a denormalised price and link-only flag for lists (the details stay the source).
-- - the joiner's attendance confirmation; feedback's "would you join again?".
-- - trekking emergency contacts (host-only after approval; deleted after the trek).
-- - per-person activity reminders.
--
-- Rollback (in this order):
--   DROP TABLE IF EXISTS public.arena_post_reminders;
--   DROP TABLE IF EXISTS public.arena_activity_emergency_contacts;
--   ALTER TABLE public.arena_activity_feedback DROP COLUMN IF EXISTS join_again;  (and restore text NOT NULL)
--   ALTER TABLE public.arena_activity_attendance DROP COLUMN IF EXISTS joiner_attended, DROP COLUMN IF EXISTS joiner_confirmed_at;
--   ALTER TABLE public.arena_posts DROP COLUMN IF EXISTS price_inr, DROP COLUMN IF EXISTS link_only;
--   ALTER TABLE public.arena_post_joins DROP COLUMN IF EXISTS note, DROP COLUMN IF EXISTS decision_note;
--   ALTER TABLE public.arena_activity_details DROP COLUMN IF EXISTS category, … (each column below);

ALTER TABLE public.arena_activity_details ALTER COLUMN kind DROP NOT NULL;
ALTER TABLE public.arena_activity_details ADD COLUMN IF NOT EXISTS category VARCHAR(20);
ALTER TABLE public.arena_activity_details ADD COLUMN IF NOT EXISTS subtype VARCHAR(40);
ALTER TABLE public.arena_activity_details ADD COLUMN IF NOT EXISTS level VARCHAR(16);
ALTER TABLE public.arena_activity_details ADD COLUMN IF NOT EXISTS cost_type VARCHAR(8) NOT NULL DEFAULT 'FREE';
ALTER TABLE public.arena_activity_details ADD COLUMN IF NOT EXISTS per_person_inr INT;
ALTER TABLE public.arena_activity_details ADD COLUMN IF NOT EXISTS cost_note VARCHAR(200);
ALTER TABLE public.arena_activity_details ADD COLUMN IF NOT EXISTS type_answers_json TEXT NOT NULL DEFAULT '{}';
ALTER TABLE public.arena_activity_details ADD COLUMN IF NOT EXISTS bring_json TEXT NOT NULL DEFAULT '[]';
ALTER TABLE public.arena_activity_details ADD COLUMN IF NOT EXISTS accessibility VARCHAR(300);
ALTER TABLE public.arena_activity_details ADD COLUMN IF NOT EXISTS indoor BOOLEAN;
ALTER TABLE public.arena_activity_details ADD COLUMN IF NOT EXISTS min_size INT;
ALTER TABLE public.arena_activity_details ADD COLUMN IF NOT EXISTS repeat_rule VARCHAR(8) NOT NULL DEFAULT 'ONCE';
ALTER TABLE public.arena_activity_details ADD COLUMN IF NOT EXISTS women_only BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_activity_details ADD COLUMN IF NOT EXISTS reach VARCHAR(8) NOT NULL DEFAULT 'NEARBY';

ALTER TABLE public.arena_post_joins ADD COLUMN IF NOT EXISTS note VARCHAR(280);
ALTER TABLE public.arena_post_joins ADD COLUMN IF NOT EXISTS decision_note VARCHAR(280);

ALTER TABLE public.arena_posts ADD COLUMN IF NOT EXISTS price_inr INT;
ALTER TABLE public.arena_posts ADD COLUMN IF NOT EXISTS link_only BOOLEAN NOT NULL DEFAULT false;

ALTER TABLE public.arena_activity_attendance ADD COLUMN IF NOT EXISTS joiner_attended BOOLEAN;
ALTER TABLE public.arena_activity_attendance ADD COLUMN IF NOT EXISTS joiner_confirmed_at TIMESTAMP(6) WITH TIME ZONE;

ALTER TABLE public.arena_activity_feedback ADD COLUMN IF NOT EXISTS join_again BOOLEAN;
ALTER TABLE public.arena_activity_feedback ALTER COLUMN text DROP NOT NULL;

CREATE TABLE IF NOT EXISTS public.arena_activity_emergency_contacts (
    id            UUID PRIMARY KEY,
    created_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at    TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    demo_content  BOOLEAN NOT NULL DEFAULT false,
    post_id       UUID NOT NULL REFERENCES public.arena_posts (id),
    user_id       UUID NOT NULL REFERENCES public.arena_users (id),
    name          VARCHAR(80) NOT NULL,
    phone         VARCHAR(20) NOT NULL,
    CONSTRAINT uk_activity_emergency_contacts UNIQUE (post_id, user_id)
);

CREATE TABLE IF NOT EXISTS public.arena_post_reminders (
    id              UUID PRIMARY KEY,
    created_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at      TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    demo_content    BOOLEAN NOT NULL DEFAULT false,
    post_id         UUID NOT NULL REFERENCES public.arena_posts (id),
    user_id         UUID NOT NULL REFERENCES public.arena_users (id),
    minutes_before  INT NOT NULL,
    remind_at       TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    sent_at         TIMESTAMP(6) WITH TIME ZONE,
    CONSTRAINT uk_post_reminders UNIQUE (post_id, user_id, minutes_before)
);
CREATE INDEX IF NOT EXISTS idx_post_reminders_due ON public.arena_post_reminders (remind_at) WHERE sent_at IS NULL;
