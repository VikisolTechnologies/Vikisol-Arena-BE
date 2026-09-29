-- FE-API-GAPS rows 14, 39 and flow A10/A11: owners edit and pause posts, and a cancel carries a
-- reason. Additive. The status column is a string; if a check constraint was generated for the
-- old enum values, widen it to include PAUSED (same guard as V20).
ALTER TABLE public.arena_posts ADD COLUMN IF NOT EXISTS cancel_reason VARCHAR(300);
ALTER TABLE public.arena_posts ADD COLUMN IF NOT EXISTS edited_at TIMESTAMP(6) WITH TIME ZONE;

DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'arena_posts_status_check'
          AND conrelid = 'public.arena_posts'::regclass
    ) THEN
        ALTER TABLE public.arena_posts DROP CONSTRAINT arena_posts_status_check;
        ALTER TABLE public.arena_posts ADD CONSTRAINT arena_posts_status_check
            CHECK (status IN ('OPEN', 'FULL', 'PAUSED', 'CLOSED', 'CANCELLED', 'EXPIRED'));
    END IF;
END $$;
