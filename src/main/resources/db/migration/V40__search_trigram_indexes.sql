-- PERFORMANCE.md (architect decision, 30 Sep 2026): trigram indexes for search's LIKE '%word%'
-- lookups (PostRepository.searchIds), which otherwise read every post.
--   - posts: title, body and place as one lowercase text. Search words never contain a space,
--     so matching the joined text is the same as matching each field.
--   - people's names and company names, for "posts by <name>".
-- If the database role may not create extensions, the indexes are skipped with a notice and
-- search keeps working the slower way, so a deploy can't fail on this.
--
-- Rollback:
--   DROP INDEX IF EXISTS public.idx_posts_search_text_trgm;
--   DROP INDEX IF EXISTS public.idx_users_name_trgm;
--   DROP INDEX IF EXISTS public.idx_enterprise_company_name_trgm;
--   (leave the pg_trgm extension; other things may use it)

DO $$
BEGIN
    BEGIN
        CREATE EXTENSION IF NOT EXISTS pg_trgm;
    EXCEPTION WHEN insufficient_privilege OR undefined_file OR feature_not_supported THEN
        RAISE NOTICE 'pg_trgm is not available (%); search runs without trigram indexes', SQLERRM;
        RETURN;
    END;
    CREATE INDEX IF NOT EXISTS idx_posts_search_text_trgm ON public.arena_posts
        USING gin ((lower(coalesce(title, '') || ' ' || body || ' ' || coalesce(location_text, ''))) gin_trgm_ops);
    CREATE INDEX IF NOT EXISTS idx_users_name_trgm ON public.arena_users USING gin ((lower(name)) gin_trgm_ops);
    CREATE INDEX IF NOT EXISTS idx_enterprise_company_name_trgm ON public.arena_enterprise_profiles
        USING gin ((lower(company_name)) gin_trgm_ops);
END
$$;
