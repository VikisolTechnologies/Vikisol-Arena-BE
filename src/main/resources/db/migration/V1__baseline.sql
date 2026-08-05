--
-- PostgreSQL database dump
--

-- Dumped from database version 16.14
-- Dumped by pg_dump version 16.14

SET statement_timeout = 0;
SET lock_timeout = 0;
SET idle_in_transaction_session_timeout = 0;
SET client_encoding = 'UTF8';
SET standard_conforming_strings = on;
SELECT pg_catalog.set_config('search_path', '', false);
SET check_function_bodies = false;
SET xmloption = content;
SET client_min_messages = warning;
SET row_security = off;

SET default_table_access_method = heap;

--
-- Name: arena_activity_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.arena_activity_events (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    description text NOT NULL,
    rationale text,
    related_job_id uuid,
    title character varying(255) NOT NULL,
    type character varying(255) NOT NULL,
    undoable boolean NOT NULL,
    user_id uuid NOT NULL,
    CONSTRAINT arena_activity_events_type_check CHECK (((type)::text = ANY ((ARRAY['SCANNED'::character varying, 'APPLIED'::character varying, 'MATCH_FOUND'::character varying, 'INTERVIEW_PROPOSED'::character varying, 'INTERVIEW_CONFIRMED'::character varying, 'MESSAGE'::character varying])::text[])))
);


--
-- Name: arena_applications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.arena_applications (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    applied_at timestamp(6) with time zone NOT NULL,
    stage character varying(255) NOT NULL,
    candidate_id uuid NOT NULL,
    job_posting_id uuid NOT NULL,
    CONSTRAINT arena_applications_stage_check CHECK (((stage)::text = ANY ((ARRAY['APPLIED'::character varying, 'SCREENING'::character varying, 'INTERVIEW'::character varying, 'OFFER'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: arena_audit_events; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.arena_audit_events (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    action character varying(255) NOT NULL,
    metadata text,
    target character varying(255),
    actor_user_id uuid NOT NULL,
    tenant_id uuid
);


--
-- Name: arena_bids; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.arena_bids (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    agent_pick boolean NOT NULL,
    amount integer NOT NULL,
    match_percentage integer NOT NULL,
    status character varying(255) NOT NULL,
    submitted_at timestamp(6) with time zone NOT NULL,
    bidder_user_id uuid NOT NULL,
    project_id uuid NOT NULL,
    CONSTRAINT arena_bids_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'SHORTLISTED'::character varying, 'WON'::character varying, 'LOST'::character varying])::text[])))
);


--
-- Name: arena_candidate_open_to; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.arena_candidate_open_to (
    candidate_id uuid NOT NULL,
    open_to character varying(255),
    CONSTRAINT arena_candidate_open_to_open_to_check CHECK (((open_to)::text = ANY ((ARRAY['FULL_TIME'::character varying, 'CONTRACT'::character varying, 'PROJECTS'::character varying])::text[])))
);


--
-- Name: arena_candidate_profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.arena_candidate_profiles (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    autonomy character varying(255) NOT NULL,
    avatar_emoji character varying(255) NOT NULL,
    bio text,
    career_health integer NOT NULL,
    auto_apply boolean NOT NULL,
    searchable_by_enterprises boolean NOT NULL,
    experience_years integer NOT NULL,
    industry character varying(255) NOT NULL,
    location character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    rate_floor integer NOT NULL,
    remote boolean NOT NULL,
    title character varying(255) NOT NULL,
    user_id uuid NOT NULL,
    cv_file_name character varying(255),
    cv_url character varying(255),
    CONSTRAINT arena_candidate_profiles_autonomy_check CHECK (((autonomy)::text = ANY ((ARRAY['MANUAL'::character varying, 'SUPERVISED'::character varying, 'AUTOPILOT'::character varying])::text[]))),
    CONSTRAINT arena_candidate_profiles_industry_check CHECK (((industry)::text = ANY ((ARRAY['ENGINEERING'::character varying, 'DESIGN'::character varying, 'SALES'::character varying, 'HEALTHCARE'::character varying, 'LOGISTICS'::character varying])::text[])))
);


--
-- Name: arena_candidate_skills; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.arena_candidate_skills (
    candidate_id uuid NOT NULL,
    name character varying(255) NOT NULL,
    verified boolean NOT NULL
);


--
-- Name: arena_conversations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.arena_conversations (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    context character varying(255),
    last_message_at timestamp(6) with time zone NOT NULL,
    last_read_ata timestamp(6) with time zone,
    last_read_atb timestamp(6) with time zone,
    user_a_id uuid NOT NULL,
    user_b_id uuid NOT NULL
);


--
-- Name: arena_credit_ledger; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.arena_credit_ledger (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    balance_after integer NOT NULL,
    delta integer NOT NULL,
    reason character varying(255) NOT NULL,
    actor_user_id uuid,
    tenant_id uuid NOT NULL
);


--
-- Name: arena_deliverables; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.arena_deliverables (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    file_url character varying(255),
    note text NOT NULL,
    status character varying(255) NOT NULL,
    submitted_at timestamp(6) with time zone NOT NULL,
    milestone_id uuid NOT NULL,
    submitted_by_user_id uuid NOT NULL,
    CONSTRAINT arena_deliverables_status_check CHECK (((status)::text = ANY ((ARRAY['SUBMITTED'::character varying, 'ACCEPTED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: arena_enterprise_hiring_for; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.arena_enterprise_hiring_for (
    enterprise_id uuid NOT NULL,
    role_name character varying(255)
);


--
-- Name: arena_enterprise_profiles; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.arena_enterprise_profiles (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    company_name character varying(255) NOT NULL,
    industry character varying(255) NOT NULL,
    logo_emoji character varying(255) NOT NULL,
    plan character varying(255) NOT NULL,
    seats_total integer NOT NULL,
    seats_used integer NOT NULL,
    size character varying(255) NOT NULL,
    unlock_credits_total integer NOT NULL,
    unlock_credits_used integer NOT NULL,
    user_id uuid NOT NULL,
    status character varying(255) DEFAULT 'ACTIVE'::character varying NOT NULL,
    CONSTRAINT arena_enterprise_profiles_industry_check CHECK (((industry)::text = ANY ((ARRAY['ENGINEERING'::character varying, 'DESIGN'::character varying, 'SALES'::character varying, 'HEALTHCARE'::character varying, 'LOGISTICS'::character varying])::text[]))),
    CONSTRAINT arena_enterprise_profiles_plan_check CHECK (((plan)::text = ANY ((ARRAY['FREE'::character varying, 'PRO'::character varying, 'ENTERPRISE'::character varying])::text[]))),
    CONSTRAINT arena_enterprise_profiles_size_check CHECK (((size)::text = ANY ((ARRAY['S_1_10'::character varying, 'S_11_50'::character varying, 'S_51_200'::character varying, 'S_201_1000'::character varying, 'S_1000_PLUS'::character varying])::text[]))),
    CONSTRAINT arena_enterprise_profiles_status_check CHECK (((status)::text = ANY ((ARRAY['ACTIVE'::character varying, 'SUSPENDED'::character varying])::text[])))
);


--
-- Name: arena_feature_flags; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.arena_feature_flags (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    description character varying(255),
    enabled boolean NOT NULL,
    key character varying(255) NOT NULL,
    label character varying(255) NOT NULL
);


--
-- Name: arena_interview_slots; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.arena_interview_slots (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    duration_minutes integer NOT NULL,
    start timestamp(6) with time zone NOT NULL,
    interview_id uuid NOT NULL
);


--
-- Name: arena_interviews; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.arena_interviews (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    confirmed_slot_id uuid,
    status character varying(255) NOT NULL,
    application_id uuid NOT NULL,
    meeting_link character varying(255),
    concerns text,
    rating integer,
    recommendation character varying(255),
    strengths text,
    submitted_at timestamp(6) with time zone,
    notes text,
    assigned_hiring_manager_user_id uuid,
    CONSTRAINT arena_interviews_recommendation_check CHECK (((recommendation)::text = ANY ((ARRAY['ADVANCE'::character varying, 'HOLD'::character varying, 'REJECT'::character varying])::text[]))),
    CONSTRAINT arena_interviews_status_check CHECK (((status)::text = ANY ((ARRAY['PROPOSED'::character varying, 'CONFIRMED'::character varying, 'COMPLETED'::character varying, 'CANCELLED'::character varying])::text[])))
);


--
-- Name: arena_invitations; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.arena_invitations (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    email character varying(255) NOT NULL,
    expires_at timestamp(6) with time zone NOT NULL,
    role character varying(255) NOT NULL,
    status character varying(255) NOT NULL,
    token character varying(255) NOT NULL,
    invited_by_user_id uuid NOT NULL,
    tenant_id uuid NOT NULL,
    CONSTRAINT arena_invitations_role_check CHECK (((role)::text = ANY ((ARRAY['TALENT'::character varying, 'RECRUITER'::character varying, 'COMPANY_ADMIN'::character varying, 'HIRING_MANAGER'::character varying, 'PLATFORM_ADMIN'::character varying])::text[]))),
    CONSTRAINT arena_invitations_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'ACCEPTED'::character varying, 'EXPIRED'::character varying, 'REVOKED'::character varying])::text[])))
);


--
-- Name: arena_job_posting_skills; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.arena_job_posting_skills (
    posting_id uuid NOT NULL,
    skill character varying(255)
);


--
-- Name: arena_job_postings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.arena_job_postings (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    description text NOT NULL,
    employment_type character varying(255) NOT NULL,
    industry character varying(255) NOT NULL,
    location character varying(255) NOT NULL,
    remote boolean NOT NULL,
    salary_max integer NOT NULL,
    salary_min integer NOT NULL,
    status character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    enterprise_id uuid NOT NULL,
    CONSTRAINT arena_job_postings_employment_type_check CHECK (((employment_type)::text = ANY ((ARRAY['FULL_TIME'::character varying, 'CONTRACT'::character varying, 'INTERNSHIP'::character varying])::text[]))),
    CONSTRAINT arena_job_postings_industry_check CHECK (((industry)::text = ANY ((ARRAY['ENGINEERING'::character varying, 'DESIGN'::character varying, 'SALES'::character varying, 'HEALTHCARE'::character varying, 'LOGISTICS'::character varying])::text[]))),
    CONSTRAINT arena_job_postings_status_check CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'PAUSED'::character varying, 'CLOSED'::character varying])::text[])))
);


--
-- Name: arena_memberships; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.arena_memberships (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    joined_at timestamp(6) with time zone,
    status character varying(255) NOT NULL,
    invited_by_user_id uuid,
    tenant_id uuid NOT NULL,
    user_id uuid NOT NULL,
    CONSTRAINT arena_memberships_status_check CHECK (((status)::text = ANY ((ARRAY['INVITED'::character varying, 'ACTIVE'::character varying, 'SUSPENDED'::character varying])::text[])))
);


--
-- Name: arena_milestones; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.arena_milestones (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    label character varying(255) NOT NULL,
    order_index integer NOT NULL,
    status character varying(255) NOT NULL,
    project_id uuid NOT NULL,
    amount integer DEFAULT 0 NOT NULL,
    CONSTRAINT arena_milestones_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'IN_PROGRESS'::character varying, 'SUBMITTED'::character varying, 'ACCEPTED'::character varying, 'REJECTED'::character varying])::text[])))
);


--
-- Name: arena_moderation_items; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.arena_moderation_items (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    reason character varying(255) NOT NULL,
    resolved_at timestamp(6) with time zone,
    status character varying(255) NOT NULL,
    job_posting_id uuid NOT NULL,
    resolved_by_user_id uuid,
    CONSTRAINT arena_moderation_items_status_check CHECK (((status)::text = ANY ((ARRAY['PENDING'::character varying, 'DISMISSED'::character varying, 'TAKEN_DOWN'::character varying])::text[])))
);


--
-- Name: arena_notifications; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.arena_notifications (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    body text NOT NULL,
    read boolean NOT NULL,
    title character varying(255) NOT NULL,
    type character varying(255) NOT NULL,
    user_id uuid NOT NULL,
    CONSTRAINT arena_notifications_type_check CHECK (((type)::text = ANY ((ARRAY['AGENT'::character varying, 'INTERVIEW'::character varying, 'BID'::character varying, 'SYSTEM'::character varying])::text[])))
);


--
-- Name: arena_project_skills; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.arena_project_skills (
    project_id uuid NOT NULL,
    skill character varying(255)
);


--
-- Name: arena_projects; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.arena_projects (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    awarded_bid_id uuid,
    budget_max integer NOT NULL,
    budget_min integer NOT NULL,
    description text NOT NULL,
    duration_weeks integer NOT NULL,
    ends_at timestamp(6) with time zone NOT NULL,
    status character varying(255) NOT NULL,
    title character varying(255) NOT NULL,
    posted_by_user_id uuid NOT NULL,
    CONSTRAINT arena_projects_status_check CHECK (((status)::text = ANY ((ARRAY['OPEN'::character varying, 'AWARDED'::character varying, 'CLOSED'::character varying])::text[])))
);


--
-- Name: arena_ratings; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.arena_ratings (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    comment text,
    direction character varying(255) NOT NULL,
    score integer NOT NULL,
    from_user_id uuid NOT NULL,
    project_id uuid NOT NULL,
    to_user_id uuid NOT NULL,
    CONSTRAINT arena_ratings_direction_check CHECK (((direction)::text = ANY ((ARRAY['CLIENT_TO_TALENT'::character varying, 'TALENT_TO_CLIENT'::character varying])::text[])))
);


--
-- Name: arena_shortlist_entries; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.arena_shortlist_entries (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    candidate_id uuid NOT NULL,
    enterprise_id uuid NOT NULL
);


--
-- Name: arena_thread_messages; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.arena_thread_messages (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    content text NOT NULL,
    conversation_id uuid NOT NULL,
    sender_user_id uuid NOT NULL
);


--
-- Name: arena_unlocked_candidates; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.arena_unlocked_candidates (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    candidate_id uuid NOT NULL,
    enterprise_id uuid NOT NULL
);


--
-- Name: arena_users; Type: TABLE; Schema: public; Owner: -
--

CREATE TABLE public.arena_users (
    id uuid NOT NULL,
    created_at timestamp(6) with time zone NOT NULL,
    updated_at timestamp(6) with time zone NOT NULL,
    email character varying(255) NOT NULL,
    name character varying(255) NOT NULL,
    password_hash character varying(255) NOT NULL,
    role character varying(255) NOT NULL,
    locked_until timestamp(6) with time zone,
    totp_secret character varying(255),
    failed_login_attempts integer DEFAULT 0 NOT NULL,
    totp_enabled boolean DEFAULT false NOT NULL
);


--
-- Name: arena_activity_events arena_activity_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_activity_events
    ADD CONSTRAINT arena_activity_events_pkey PRIMARY KEY (id);


--
-- Name: arena_applications arena_applications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_applications
    ADD CONSTRAINT arena_applications_pkey PRIMARY KEY (id);


--
-- Name: arena_audit_events arena_audit_events_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_audit_events
    ADD CONSTRAINT arena_audit_events_pkey PRIMARY KEY (id);


--
-- Name: arena_bids arena_bids_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_bids
    ADD CONSTRAINT arena_bids_pkey PRIMARY KEY (id);


--
-- Name: arena_candidate_profiles arena_candidate_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_candidate_profiles
    ADD CONSTRAINT arena_candidate_profiles_pkey PRIMARY KEY (id);


--
-- Name: arena_conversations arena_conversations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_conversations
    ADD CONSTRAINT arena_conversations_pkey PRIMARY KEY (id);


--
-- Name: arena_credit_ledger arena_credit_ledger_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_credit_ledger
    ADD CONSTRAINT arena_credit_ledger_pkey PRIMARY KEY (id);


--
-- Name: arena_deliverables arena_deliverables_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_deliverables
    ADD CONSTRAINT arena_deliverables_pkey PRIMARY KEY (id);


--
-- Name: arena_enterprise_profiles arena_enterprise_profiles_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_enterprise_profiles
    ADD CONSTRAINT arena_enterprise_profiles_pkey PRIMARY KEY (id);


--
-- Name: arena_feature_flags arena_feature_flags_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_feature_flags
    ADD CONSTRAINT arena_feature_flags_pkey PRIMARY KEY (id);


--
-- Name: arena_interview_slots arena_interview_slots_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_interview_slots
    ADD CONSTRAINT arena_interview_slots_pkey PRIMARY KEY (id);


--
-- Name: arena_interviews arena_interviews_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_interviews
    ADD CONSTRAINT arena_interviews_pkey PRIMARY KEY (id);


--
-- Name: arena_invitations arena_invitations_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_invitations
    ADD CONSTRAINT arena_invitations_pkey PRIMARY KEY (id);


--
-- Name: arena_job_postings arena_job_postings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_job_postings
    ADD CONSTRAINT arena_job_postings_pkey PRIMARY KEY (id);


--
-- Name: arena_memberships arena_memberships_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_memberships
    ADD CONSTRAINT arena_memberships_pkey PRIMARY KEY (id);


--
-- Name: arena_milestones arena_milestones_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_milestones
    ADD CONSTRAINT arena_milestones_pkey PRIMARY KEY (id);


--
-- Name: arena_moderation_items arena_moderation_items_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_moderation_items
    ADD CONSTRAINT arena_moderation_items_pkey PRIMARY KEY (id);


--
-- Name: arena_notifications arena_notifications_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_notifications
    ADD CONSTRAINT arena_notifications_pkey PRIMARY KEY (id);


--
-- Name: arena_projects arena_projects_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_projects
    ADD CONSTRAINT arena_projects_pkey PRIMARY KEY (id);


--
-- Name: arena_ratings arena_ratings_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_ratings
    ADD CONSTRAINT arena_ratings_pkey PRIMARY KEY (id);


--
-- Name: arena_shortlist_entries arena_shortlist_entries_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_shortlist_entries
    ADD CONSTRAINT arena_shortlist_entries_pkey PRIMARY KEY (id);


--
-- Name: arena_thread_messages arena_thread_messages_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_thread_messages
    ADD CONSTRAINT arena_thread_messages_pkey PRIMARY KEY (id);


--
-- Name: arena_unlocked_candidates arena_unlocked_candidates_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_unlocked_candidates
    ADD CONSTRAINT arena_unlocked_candidates_pkey PRIMARY KEY (id);


--
-- Name: arena_users arena_users_pkey; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_users
    ADD CONSTRAINT arena_users_pkey PRIMARY KEY (id);


--
-- Name: arena_users uk2nbwevgpt576ryaipuujhlp0v; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_users
    ADD CONSTRAINT uk2nbwevgpt576ryaipuujhlp0v UNIQUE (email);


--
-- Name: arena_ratings uk3upq2fa22yavuibdchptv4j1g; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_ratings
    ADD CONSTRAINT uk3upq2fa22yavuibdchptv4j1g UNIQUE (project_id, from_user_id, direction);


--
-- Name: arena_invitations uk682jd8u2142ootf300qoc8ddo; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_invitations
    ADD CONSTRAINT uk682jd8u2142ootf300qoc8ddo UNIQUE (token);


--
-- Name: arena_unlocked_candidates uk8kd68r7wfncr3fu42ytvl8xmu; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_unlocked_candidates
    ADD CONSTRAINT uk8kd68r7wfncr3fu42ytvl8xmu UNIQUE (enterprise_id, candidate_id);


--
-- Name: arena_interviews uk9w3f4b4v2aqg49krfa5i1ll3d; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_interviews
    ADD CONSTRAINT uk9w3f4b4v2aqg49krfa5i1ll3d UNIQUE (application_id);


--
-- Name: arena_candidate_profiles ukdjfyn3wp0or7gjthb6m80w95j; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_candidate_profiles
    ADD CONSTRAINT ukdjfyn3wp0or7gjthb6m80w95j UNIQUE (user_id);


--
-- Name: arena_enterprise_profiles ukhv4g0k3tik6a8dr48e4x5l8pv; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_enterprise_profiles
    ADD CONSTRAINT ukhv4g0k3tik6a8dr48e4x5l8pv UNIQUE (user_id);


--
-- Name: arena_feature_flags ukiiqmjuirw1puwoppr53j8aqqj; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_feature_flags
    ADD CONSTRAINT ukiiqmjuirw1puwoppr53j8aqqj UNIQUE (key);


--
-- Name: arena_shortlist_entries ukkx3q02b1w5c0ufo9lw0cmar0i; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_shortlist_entries
    ADD CONSTRAINT ukkx3q02b1w5c0ufo9lw0cmar0i UNIQUE (enterprise_id, candidate_id);


--
-- Name: arena_memberships ukl1pbs9ucgyw18eyanc4wmm47a; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_memberships
    ADD CONSTRAINT ukl1pbs9ucgyw18eyanc4wmm47a UNIQUE (user_id);


--
-- Name: arena_applications ukn6cwrvtanknjeevu0qbpqkgbu; Type: CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_applications
    ADD CONSTRAINT ukn6cwrvtanknjeevu0qbpqkgbu UNIQUE (candidate_id, job_posting_id);


--
-- Name: arena_ratings fk1kk4gnwk93pf1dx7ga1qrxh8b; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_ratings
    ADD CONSTRAINT fk1kk4gnwk93pf1dx7ga1qrxh8b FOREIGN KEY (to_user_id) REFERENCES public.arena_users(id);


--
-- Name: arena_credit_ledger fk1pseu3bt0e1c31hyyxf78tujm; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_credit_ledger
    ADD CONSTRAINT fk1pseu3bt0e1c31hyyxf78tujm FOREIGN KEY (tenant_id) REFERENCES public.arena_enterprise_profiles(id);


--
-- Name: arena_thread_messages fk4jwbpk1w1jurln3gm9wqq2gx; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_thread_messages
    ADD CONSTRAINT fk4jwbpk1w1jurln3gm9wqq2gx FOREIGN KEY (conversation_id) REFERENCES public.arena_conversations(id);


--
-- Name: arena_deliverables fk5cqyilkg6whimgnyxs693gua9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_deliverables
    ADD CONSTRAINT fk5cqyilkg6whimgnyxs693gua9 FOREIGN KEY (submitted_by_user_id) REFERENCES public.arena_users(id);


--
-- Name: arena_candidate_open_to fk65op4q8tqxtu2igpf2735imik; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_candidate_open_to
    ADD CONSTRAINT fk65op4q8tqxtu2igpf2735imik FOREIGN KEY (candidate_id) REFERENCES public.arena_candidate_profiles(id);


--
-- Name: arena_invitations fk6fje8e0s44b7jnuau54ae49bd; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_invitations
    ADD CONSTRAINT fk6fje8e0s44b7jnuau54ae49bd FOREIGN KEY (tenant_id) REFERENCES public.arena_enterprise_profiles(id);


--
-- Name: arena_applications fk6u1tqbfjc9ciajkarp7jcpck7; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_applications
    ADD CONSTRAINT fk6u1tqbfjc9ciajkarp7jcpck7 FOREIGN KEY (job_posting_id) REFERENCES public.arena_job_postings(id);


--
-- Name: arena_audit_events fk89xg9hrjl9ndgqb97owwu3r4i; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_audit_events
    ADD CONSTRAINT fk89xg9hrjl9ndgqb97owwu3r4i FOREIGN KEY (actor_user_id) REFERENCES public.arena_users(id);


--
-- Name: arena_moderation_items fk8iahxs2hywaq1cta7mftu3enb; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_moderation_items
    ADD CONSTRAINT fk8iahxs2hywaq1cta7mftu3enb FOREIGN KEY (resolved_by_user_id) REFERENCES public.arena_users(id);


--
-- Name: arena_bids fk9fs6vvbrcscdn5qboie27v1rf; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_bids
    ADD CONSTRAINT fk9fs6vvbrcscdn5qboie27v1rf FOREIGN KEY (bidder_user_id) REFERENCES public.arena_users(id);


--
-- Name: arena_ratings fk9k5dugjt5otcjkl79kiulnvad; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_ratings
    ADD CONSTRAINT fk9k5dugjt5otcjkl79kiulnvad FOREIGN KEY (project_id) REFERENCES public.arena_projects(id);


--
-- Name: arena_bids fkb0l4jtycc3xh4xyie5ieun7oe; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_bids
    ADD CONSTRAINT fkb0l4jtycc3xh4xyie5ieun7oe FOREIGN KEY (project_id) REFERENCES public.arena_projects(id);


--
-- Name: arena_projects fkc4g8d31876acu3yn7yr518c1u; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_projects
    ADD CONSTRAINT fkc4g8d31876acu3yn7yr518c1u FOREIGN KEY (posted_by_user_id) REFERENCES public.arena_users(id);


--
-- Name: arena_memberships fkc839ntfuaqh3dttjssf9g2n1l; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_memberships
    ADD CONSTRAINT fkc839ntfuaqh3dttjssf9g2n1l FOREIGN KEY (tenant_id) REFERENCES public.arena_enterprise_profiles(id);


--
-- Name: arena_interviews fkdsxjdt9r5dcfudmnqjbjqic5n; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_interviews
    ADD CONSTRAINT fkdsxjdt9r5dcfudmnqjbjqic5n FOREIGN KEY (assigned_hiring_manager_user_id) REFERENCES public.arena_users(id);


--
-- Name: arena_thread_messages fkdwh1brng4qbqtvyuoq4kfpia7; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_thread_messages
    ADD CONSTRAINT fkdwh1brng4qbqtvyuoq4kfpia7 FOREIGN KEY (sender_user_id) REFERENCES public.arena_users(id);


--
-- Name: arena_interviews fkere2r892tbdxlt7kmyfkos0pr; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_interviews
    ADD CONSTRAINT fkere2r892tbdxlt7kmyfkos0pr FOREIGN KEY (application_id) REFERENCES public.arena_applications(id);


--
-- Name: arena_shortlist_entries fkeuour8oiilr39j6rqnthebpta; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_shortlist_entries
    ADD CONSTRAINT fkeuour8oiilr39j6rqnthebpta FOREIGN KEY (candidate_id) REFERENCES public.arena_candidate_profiles(id);


--
-- Name: arena_candidate_skills fkf27wnidldc2lcbfjfo24v7wbx; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_candidate_skills
    ADD CONSTRAINT fkf27wnidldc2lcbfjfo24v7wbx FOREIGN KEY (candidate_id) REFERENCES public.arena_candidate_profiles(id);


--
-- Name: arena_enterprise_profiles fkfq30o5ks5jrvtsmo78fn61s9e; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_enterprise_profiles
    ADD CONSTRAINT fkfq30o5ks5jrvtsmo78fn61s9e FOREIGN KEY (user_id) REFERENCES public.arena_users(id);


--
-- Name: arena_shortlist_entries fkfvynfpd9xf8qoql0aqhmgluaq; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_shortlist_entries
    ADD CONSTRAINT fkfvynfpd9xf8qoql0aqhmgluaq FOREIGN KEY (enterprise_id) REFERENCES public.arena_enterprise_profiles(id);


--
-- Name: arena_applications fkge3y41ic98aw8i6uv2jsqk7k6; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_applications
    ADD CONSTRAINT fkge3y41ic98aw8i6uv2jsqk7k6 FOREIGN KEY (candidate_id) REFERENCES public.arena_candidate_profiles(id);


--
-- Name: arena_milestones fkh3uk26iyioxmgn0otjhxttos9; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_milestones
    ADD CONSTRAINT fkh3uk26iyioxmgn0otjhxttos9 FOREIGN KEY (project_id) REFERENCES public.arena_projects(id);


--
-- Name: arena_notifications fkhdvcilt7h3o8wolsegj9tr8mp; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_notifications
    ADD CONSTRAINT fkhdvcilt7h3o8wolsegj9tr8mp FOREIGN KEY (user_id) REFERENCES public.arena_users(id);


--
-- Name: arena_activity_events fkj4gv5o4n2unyj5m8b41d1vd74; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_activity_events
    ADD CONSTRAINT fkj4gv5o4n2unyj5m8b41d1vd74 FOREIGN KEY (user_id) REFERENCES public.arena_users(id);


--
-- Name: arena_memberships fkk2ilmmctojyqqt9ybxu1ln4kv; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_memberships
    ADD CONSTRAINT fkk2ilmmctojyqqt9ybxu1ln4kv FOREIGN KEY (user_id) REFERENCES public.arena_users(id);


--
-- Name: arena_credit_ledger fkktceh4c5wfpupq4xdimoj0q1p; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_credit_ledger
    ADD CONSTRAINT fkktceh4c5wfpupq4xdimoj0q1p FOREIGN KEY (actor_user_id) REFERENCES public.arena_users(id);


--
-- Name: arena_job_posting_skills fkliud5ralibheucq38o1kygsxi; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_job_posting_skills
    ADD CONSTRAINT fkliud5ralibheucq38o1kygsxi FOREIGN KEY (posting_id) REFERENCES public.arena_job_postings(id);


--
-- Name: arena_enterprise_hiring_for fklr12way051f43e82jdui3u0v3; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_enterprise_hiring_for
    ADD CONSTRAINT fklr12way051f43e82jdui3u0v3 FOREIGN KEY (enterprise_id) REFERENCES public.arena_enterprise_profiles(id);


--
-- Name: arena_memberships fkmb6uyqa4k8qfsu7cnxdt0i9h8; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_memberships
    ADD CONSTRAINT fkmb6uyqa4k8qfsu7cnxdt0i9h8 FOREIGN KEY (invited_by_user_id) REFERENCES public.arena_users(id);


--
-- Name: arena_unlocked_candidates fkmjoe1blppbyyyppyof54owvao; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_unlocked_candidates
    ADD CONSTRAINT fkmjoe1blppbyyyppyof54owvao FOREIGN KEY (enterprise_id) REFERENCES public.arena_enterprise_profiles(id);


--
-- Name: arena_ratings fkmyi7ovn9a5c1v56p4l7cv7nw7; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_ratings
    ADD CONSTRAINT fkmyi7ovn9a5c1v56p4l7cv7nw7 FOREIGN KEY (from_user_id) REFERENCES public.arena_users(id);


--
-- Name: arena_project_skills fknntn7phdwak75brxodusot46l; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_project_skills
    ADD CONSTRAINT fknntn7phdwak75brxodusot46l FOREIGN KEY (project_id) REFERENCES public.arena_projects(id);


--
-- Name: arena_conversations fknqv4q3r68td0l2sit71sifnsc; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_conversations
    ADD CONSTRAINT fknqv4q3r68td0l2sit71sifnsc FOREIGN KEY (user_a_id) REFERENCES public.arena_users(id);


--
-- Name: arena_conversations fknwwfa69fwen550a9c89kd88p7; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_conversations
    ADD CONSTRAINT fknwwfa69fwen550a9c89kd88p7 FOREIGN KEY (user_b_id) REFERENCES public.arena_users(id);


--
-- Name: arena_job_postings fkosgmfmromfmorark06mqchu2s; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_job_postings
    ADD CONSTRAINT fkosgmfmromfmorark06mqchu2s FOREIGN KEY (enterprise_id) REFERENCES public.arena_enterprise_profiles(id);


--
-- Name: arena_candidate_profiles fkpeewn6p7ttyt5ot7ae816gjfr; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_candidate_profiles
    ADD CONSTRAINT fkpeewn6p7ttyt5ot7ae816gjfr FOREIGN KEY (user_id) REFERENCES public.arena_users(id);


--
-- Name: arena_deliverables fkpuni2w8whr7k6was00miecawt; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_deliverables
    ADD CONSTRAINT fkpuni2w8whr7k6was00miecawt FOREIGN KEY (milestone_id) REFERENCES public.arena_milestones(id);


--
-- Name: arena_audit_events fkqducwkleomxajbogqr2ku2viu; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_audit_events
    ADD CONSTRAINT fkqducwkleomxajbogqr2ku2viu FOREIGN KEY (tenant_id) REFERENCES public.arena_enterprise_profiles(id);


--
-- Name: arena_moderation_items fkqtns8p5bi9p8kauxp21t6w59f; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_moderation_items
    ADD CONSTRAINT fkqtns8p5bi9p8kauxp21t6w59f FOREIGN KEY (job_posting_id) REFERENCES public.arena_job_postings(id);


--
-- Name: arena_interview_slots fkr70xoyni9u1qc5catgnb6b05h; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_interview_slots
    ADD CONSTRAINT fkr70xoyni9u1qc5catgnb6b05h FOREIGN KEY (interview_id) REFERENCES public.arena_interviews(id);


--
-- Name: arena_unlocked_candidates fkre5ddakqq5o2iq0i4djlf69nu; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_unlocked_candidates
    ADD CONSTRAINT fkre5ddakqq5o2iq0i4djlf69nu FOREIGN KEY (candidate_id) REFERENCES public.arena_candidate_profiles(id);


--
-- Name: arena_invitations fkrpeds105qv53nqs3u5tyihi31; Type: FK CONSTRAINT; Schema: public; Owner: -
--

ALTER TABLE ONLY public.arena_invitations
    ADD CONSTRAINT fkrpeds105qv53nqs3u5tyihi31 FOREIGN KEY (invited_by_user_id) REFERENCES public.arena_users(id);


--
-- PostgreSQL database dump complete
--

