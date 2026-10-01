-- PERFORMANCE.md (architect decision, 30 Sep 2026): trigram indexes for search's LIKE '%word%'
-- lookups (PostRepository.searchIds), which otherwise read every post.
--   - posts: title, body and place as one lowercase text. Search words never contain a space,
--     so matching the joined text is the same as matching each field.
--   - people's names and company names, for "posts by <name>".
-- If the database role may not create extensions (or the gin_trgm_ops operator class can't be
-- resolved at all, e.g. some other role installed pg_trgm into a schema this role's search_path
-- doesn't include), the indexes are skipped with a notice and search keeps working the slower
-- way, so a deploy can't fail on this.
--
-- Rollback:
--   DROP INDEX IF EXISTS public.idx_posts_search_text_trgm;
--   DROP INDEX IF EXISTS public.idx_users_name_trgm;
--   DROP INDEX IF EXISTS public.idx_enterprise_company_name_trgm;
--   (leave the pg_trgm extension; other things may use it)

DO $$
BEGIN
    -- ARCHITECT-REVIEW-BE-1 SHOULD-FIX (migrations): pin the search_path for this block so
    -- gin_trgm_ops resolves even when the connecting role's own default search_path doesn't
    -- include the schema pg_trgm's operator classes live in (Postgres always installs an
    -- extension's objects into "public" unless told otherwise, but a role's search_path is a
    -- session setting, not a database guarantee - a managed Postgres role on another environment
    -- could have a narrower one). SET LOCAL is scoped to this transaction, same as the
    -- surrounding migration.
    SET LOCAL search_path TO public, pg_catalog;
    BEGIN
        CREATE EXTENSION IF NOT EXISTS pg_trgm;
        -- Kept inside the same exception handler as the extension creation, not after it -
        -- previously a privilege/availability problem on the extension alone was caught, but an
        -- operator-class resolution failure on the indexes themselves (the same underlying
        -- search_path issue this SET LOCAL now prevents) was not, and would have failed the
        -- whole migration/deploy instead of degrading to the slower, index-free search path.
        CREATE INDEX IF NOT EXISTS idx_posts_search_text_trgm ON public.arena_posts
            USING gin ((lower(coalesce(title, '') || ' ' || body || ' ' || coalesce(location_text, ''))) gin_trgm_ops);
        CREATE INDEX IF NOT EXISTS idx_users_name_trgm ON public.arena_users USING gin ((lower(name)) gin_trgm_ops);
        CREATE INDEX IF NOT EXISTS idx_enterprise_company_name_trgm ON public.arena_enterprise_profiles
            USING gin ((lower(company_name)) gin_trgm_ops);
    EXCEPTION WHEN insufficient_privilege OR undefined_file OR feature_not_supported OR undefined_object THEN
        RAISE NOTICE 'pg_trgm is not available (%); search runs without trigram indexes', SQLERRM;
    END;
END
$$;
