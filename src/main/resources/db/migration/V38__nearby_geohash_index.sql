-- PERFORMANCE.md: nearby discovery looks posts up by geohash prefix instead of scoring a window
-- of the newest posts. Geohashes compare in byte order (COLLATE "C") so a prefix is a plain
-- index range whatever the database's default collation; the index only holds open posts with
-- a location, which is all the lookup ever reads.
--
-- Rollback: DROP INDEX IF EXISTS public.idx_posts_open_geohash_c;

CREATE INDEX IF NOT EXISTS idx_posts_open_geohash_c
    ON public.arena_posts ((geohash COLLATE "C"))
    WHERE status = 'OPEN' AND geohash IS NOT NULL;
