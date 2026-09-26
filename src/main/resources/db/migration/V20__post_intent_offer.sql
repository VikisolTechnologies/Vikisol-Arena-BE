-- OFFER is a post kind, additive. The column is already a string. If a check
-- constraint was generated for the previous enum values, widen it. If none
-- exists, leave the column alone.
DO $$
BEGIN
    IF EXISTS (
        SELECT 1 FROM pg_constraint
        WHERE conname = 'arena_posts_intent_type_check'
          AND conrelid = 'public.arena_posts'::regclass
    ) THEN
        ALTER TABLE public.arena_posts DROP CONSTRAINT arena_posts_intent_type_check;
        ALTER TABLE public.arena_posts ADD CONSTRAINT arena_posts_intent_type_check
            CHECK (intent_type IN ('ACTIVITY', 'ASK', 'UPDATE', 'COMPANY', 'OFFER'));
    END IF;
END $$;
