-- Performance audit (2026-08-06): V1__baseline.sql (a pg_dump of Hibernate's auto-DDL) never
-- created a single index beyond what UNIQUE constraints happened to cover. Every foreign-key
-- lookup and every list/search/audit query has been doing a sequential scan. This migration
-- adds indexes for the columns actually driving WHERE/JOIN/ORDER BY in the repository layer.

-- applications: pipeline view queries "all applications for a posting"; the existing unique
-- constraint on (candidate_id, job_posting_id) only serves candidate_id as a leading column.
CREATE INDEX IF NOT EXISTS idx_applications_job_posting_id ON public.arena_applications (job_posting_id);
CREATE INDEX IF NOT EXISTS idx_applications_stage ON public.arena_applications (job_posting_id, stage);

-- audit events: tenant-scoped, most-recent-first is the standard access pattern; actor lookups
-- (AdminDashboardService's per-member query) need their own index.
CREATE INDEX IF NOT EXISTS idx_audit_events_tenant_created ON public.arena_audit_events (tenant_id, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_audit_events_actor_user_id ON public.arena_audit_events (actor_user_id);

-- bids: per-project bid list (marketplace) and per-bidder "my bids".
CREATE INDEX IF NOT EXISTS idx_bids_project_id ON public.arena_bids (project_id);
CREATE INDEX IF NOT EXISTS idx_bids_bidder_user_id ON public.arena_bids (bidder_user_id);

-- element-collection join tables: candidate skills/open-to are joined on every search hit.
CREATE INDEX IF NOT EXISTS idx_candidate_skills_candidate_id ON public.arena_candidate_skills (candidate_id);
CREATE INDEX IF NOT EXISTS idx_candidate_open_to_candidate_id ON public.arena_candidate_open_to (candidate_id);

-- candidate search filters (TalentSearchService): searchable flag is checked on almost every
-- query, combined with industry; a partial index keeps it small since most rows will be true.
CREATE INDEX IF NOT EXISTS idx_candidate_profiles_searchable_industry
    ON public.arena_candidate_profiles (industry) WHERE searchable_by_enterprises = true;

-- conversations: "my conversations" looks up by either side of the pair.
CREATE INDEX IF NOT EXISTS idx_conversations_user_a_id ON public.arena_conversations (user_a_id);
CREATE INDEX IF NOT EXISTS idx_conversations_user_b_id ON public.arena_conversations (user_b_id);

-- thread messages: fetching a conversation's messages, ordered, is the hottest messaging query.
CREATE INDEX IF NOT EXISTS idx_thread_messages_conversation_id ON public.arena_thread_messages (conversation_id, created_at);

-- credit ledger: tenant billing history.
CREATE INDEX IF NOT EXISTS idx_credit_ledger_tenant_id ON public.arena_credit_ledger (tenant_id);

-- deliverables: per-milestone lookup.
CREATE INDEX IF NOT EXISTS idx_deliverables_milestone_id ON public.arena_deliverables (milestone_id);

-- interviews: hiring-manager's assigned interviews (InterviewService.getMyAssignedInterviews).
CREATE INDEX IF NOT EXISTS idx_interviews_assigned_hm ON public.arena_interviews (assigned_hiring_manager_user_id);
CREATE INDEX IF NOT EXISTS idx_interview_slots_interview_id ON public.arena_interview_slots (interview_id);

-- invitations: tenant + inviter lookups.
CREATE INDEX IF NOT EXISTS idx_invitations_tenant_id ON public.arena_invitations (tenant_id);
CREATE INDEX IF NOT EXISTS idx_invitations_invited_by ON public.arena_invitations (invited_by_user_id);

-- job posting skills: joined on every posting/job read (JobMapper, JobPostingMapper).
CREATE INDEX IF NOT EXISTS idx_job_posting_skills_posting_id ON public.arena_job_posting_skills (posting_id);

-- job postings: public browse (status = OPEN) and "my postings" (enterprise_id).
CREATE INDEX IF NOT EXISTS idx_job_postings_enterprise_id ON public.arena_job_postings (enterprise_id);
CREATE INDEX IF NOT EXISTS idx_job_postings_status ON public.arena_job_postings (status);

-- memberships: per-request tenant resolution (every enterprise-scoped request does this lookup
-- - DECISIONS.md's enterprise-suite design assumed this was "one extra indexed query"; it never
-- actually had an index until now) plus per-tenant team listing.
CREATE INDEX IF NOT EXISTS idx_memberships_user_id ON public.arena_memberships (user_id);
CREATE INDEX IF NOT EXISTS idx_memberships_tenant_id ON public.arena_memberships (tenant_id);

-- milestones: per-project ordering.
CREATE INDEX IF NOT EXISTS idx_milestones_project_id ON public.arena_milestones (project_id, order_index);

-- moderation queue: platform admin filters by status, most-recent-first.
CREATE INDEX IF NOT EXISTS idx_moderation_items_status ON public.arena_moderation_items (status, created_at DESC);
CREATE INDEX IF NOT EXISTS idx_moderation_items_job_posting_id ON public.arena_moderation_items (job_posting_id);

-- notifications: "my unread notifications" is the standard poll/badge-count query.
CREATE INDEX IF NOT EXISTS idx_notifications_user_unread ON public.arena_notifications (user_id, read, created_at DESC);

-- project skills: joined on every project/marketplace read.
CREATE INDEX IF NOT EXISTS idx_project_skills_project_id ON public.arena_project_skills (project_id);

-- projects: open marketplace browse (ProjectService.getOpenProjects).
CREATE INDEX IF NOT EXISTS idx_projects_status ON public.arena_projects (status);
CREATE INDEX IF NOT EXISTS idx_projects_posted_by_user_id ON public.arena_projects (posted_by_user_id);

-- ratings: "ratings received" lookup (to_user_id isn't covered by the from_user_id-leading
-- unique constraint).
CREATE INDEX IF NOT EXISTS idx_ratings_to_user_id ON public.arena_ratings (to_user_id);

-- shortlist / unlocked candidates: candidate_id isn't covered by the enterprise_id-leading
-- unique constraints, but "has this enterprise unlocked/shortlisted candidate X" and reverse
-- lookups both happen.
CREATE INDEX IF NOT EXISTS idx_shortlist_entries_candidate_id ON public.arena_shortlist_entries (candidate_id);
CREATE INDEX IF NOT EXISTS idx_unlocked_candidates_candidate_id ON public.arena_unlocked_candidates (candidate_id);

-- activity events: "my activity feed", most-recent-first.
CREATE INDEX IF NOT EXISTS idx_activity_events_user_id ON public.arena_activity_events (user_id, created_at DESC);

-- enterprise_hiring_for: joined when rendering an enterprise profile.
CREATE INDEX IF NOT EXISTS idx_enterprise_hiring_for_enterprise_id ON public.arena_enterprise_hiring_for (enterprise_id);
