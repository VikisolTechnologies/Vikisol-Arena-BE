-- Post-spec reconciliation pass, after ARENA-V2-PRODUCT-ARCHITECTURE.md's actual text became
-- available (was only ever pasted in chat before this - see DECISIONS.md). Two changes:
--
-- 1. §4 safety-audit fix: posts are now directly reportable (not just via a Room), so
--    ModerationItem needs a nullable post_id counterpart to job_posting_id/room_id.
-- 2. §3.5/§6 "Company posts appear in the feed": arena_posts.author_company_id already existed
--    as a plain UUID column since V4 (reserved, never populated) - this just adds the FK
--    constraint now that it's a real JPA relation (EnterpriseProfile) instead of a raw column.
--    No column type/nullability change, so no Hibernate ddl-auto:validate impact either way.

ALTER TABLE public.arena_moderation_items ADD COLUMN post_id UUID REFERENCES arena_posts (id);
CREATE INDEX idx_moderation_items_post_id ON public.arena_moderation_items (post_id) WHERE post_id IS NOT NULL;

ALTER TABLE public.arena_posts ADD CONSTRAINT fk_posts_author_company FOREIGN KEY (author_company_id) REFERENCES arena_enterprise_profiles (id);
