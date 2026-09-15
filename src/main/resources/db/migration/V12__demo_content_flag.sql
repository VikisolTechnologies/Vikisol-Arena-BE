-- ARENA-WEB-AND-SEED.md Part 4. BaseEntity.demoContent is a @MappedSuperclass field, so every
-- entity extending it (all 35 tables below) needs the column - ddl-auto: validate is active
-- (see application.yml) and startup fails loudly on any mismatch. `not null default false` means
-- every existing row (including the original DataSeeder's output) is correctly, honestly
-- classified as NOT demo content - that seeder's data is real seeded bootstrap data, not the
-- new labeled/removable demo-content overlay this flag is for. See DemoContentService.

ALTER TABLE public.arena_activity_events       ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_agent_conversations    ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_agent_messages         ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_applications           ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_audit_events           ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_bids                   ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_candidate_profiles     ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_conversations          ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_credit_ledger          ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_deliverables           ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_enterprise_profiles    ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_feature_flags          ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_follows                ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_interview_slots        ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_interviews             ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_invitations            ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_job_postings           ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_memberships            ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_milestones             ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_moderation_items       ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_notifications          ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_post_comments          ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_post_joins             ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_post_reactions         ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_post_saves             ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_posts                  ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_projects               ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_ratings                ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_room_members           ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_room_messages          ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_room_reports           ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_rooms                  ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_shortlist_entries      ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_thread_messages        ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_unlocked_candidates    ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_user_blocks            ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_users                  ADD COLUMN demo_content BOOLEAN NOT NULL DEFAULT false;

-- Fast lookup for the removal command (DELETE ... WHERE demo_content = true) on the two tables
-- that matter most for bulk cleanup volume.
CREATE INDEX idx_posts_demo_content ON public.arena_posts(demo_content) WHERE demo_content = true;
CREATE INDEX idx_users_demo_content ON public.arena_users(demo_content) WHERE demo_content = true;
