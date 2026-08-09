-- ARENA-V2-PRODUCT-ARCHITECTURE.md Phase B: location/consent, verification tiers, age-gating,
-- activity lifecycle, blocks, mute, and moderation-queue generalization. Matches Hibernate's
-- expected DDL exactly (ddl-auto: validate is active) - see entities in auth/profile/posts/
-- rooms/platform for the Java side of every column below.

-- users: verification tiers (§4) + self-attested age-gating (§4) - see DECISIONS.md for both.
ALTER TABLE public.arena_users ADD COLUMN date_of_birth DATE;
ALTER TABLE public.arena_users ADD COLUMN phone_number VARCHAR(255);
ALTER TABLE public.arena_users ADD COLUMN phone_verified BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE public.arena_users ADD COLUMN pending_otp_hash VARCHAR(255);
ALTER TABLE public.arena_users ADD COLUMN pending_otp_expires_at TIMESTAMP(6);
ALTER TABLE public.arena_users ADD COLUMN verification_level VARCHAR(255) NOT NULL DEFAULT 'BASIC';

-- candidate_profiles: location consent (§5) - geohash/approx_lat/approx_lng are ALWAYS an
-- approximation, never the raw device coordinate (see DECISIONS.md's location entry).
ALTER TABLE public.arena_candidate_profiles ADD COLUMN location_consent VARCHAR(255) NOT NULL DEFAULT 'OFF';
ALTER TABLE public.arena_candidate_profiles ADD COLUMN home_city VARCHAR(255);
ALTER TABLE public.arena_candidate_profiles ADD COLUMN geohash VARCHAR(255);
ALTER TABLE public.arena_candidate_profiles ADD COLUMN approx_lat DOUBLE PRECISION;
ALTER TABLE public.arena_candidate_profiles ADD COLUMN approx_lng DOUBLE PRECISION;
CREATE INDEX idx_candidate_profiles_geohash ON public.arena_candidate_profiles (geohash) WHERE geohash IS NOT NULL;

-- posts: per-post geo (same approximation guarantee), the real "exact meeting point" (only ever
-- returned to the author/approved room members - see PostMapper), creator-required verification
-- level to join (§4), and remindedAt for PostLifecycleScheduler.
ALTER TABLE public.arena_posts ADD COLUMN geohash VARCHAR(255);
ALTER TABLE public.arena_posts ADD COLUMN approx_lat DOUBLE PRECISION;
ALTER TABLE public.arena_posts ADD COLUMN approx_lng DOUBLE PRECISION;
ALTER TABLE public.arena_posts ADD COLUMN exact_meeting_point TEXT;
ALTER TABLE public.arena_posts ADD COLUMN required_verification_level VARCHAR(255);
ALTER TABLE public.arena_posts ADD COLUMN reminded_at TIMESTAMP(6);
CREATE INDEX idx_posts_geohash ON public.arena_posts (geohash) WHERE geohash IS NOT NULL;
CREATE INDEX idx_posts_starts_at ON public.arena_posts (starts_at) WHERE starts_at IS NOT NULL;

-- room_members: mute (§4 "mute everywhere").
ALTER TABLE public.arena_room_members ADD COLUMN muted BOOLEAN NOT NULL DEFAULT false;

-- user_blocks: new table, same independent-join-entity shape as arena_follows (§4 "block").
CREATE TABLE arena_user_blocks (
    id               UUID PRIMARY KEY,
    created_at       TIMESTAMP(6) NOT NULL,
    updated_at       TIMESTAMP(6) NOT NULL,
    blocker_user_id  UUID NOT NULL REFERENCES arena_users (id),
    blocked_user_id  UUID NOT NULL REFERENCES arena_users (id),
    CONSTRAINT uk_user_blocks_blocker_blocked UNIQUE (blocker_user_id, blocked_user_id)
);
CREATE INDEX idx_user_blocks_blocker_user_id ON public.arena_user_blocks (blocker_user_id);
CREATE INDEX idx_user_blocks_blocked_user_id ON public.arena_user_blocks (blocked_user_id);

-- moderation_items: generalized additively (see DECISIONS.md) - job_posting_id becomes nullable
-- (a ROOM-type item has none), room_id/reporter_user_id are the new nullable counterparts,
-- content_type says which one is actually populated. Existing rows (all JobPosting auto-flags
-- so far) get 'JOB_POSTING' via the column default, correctly preserving their current meaning.
ALTER TABLE public.arena_moderation_items ALTER COLUMN job_posting_id DROP NOT NULL;
ALTER TABLE public.arena_moderation_items ADD COLUMN content_type VARCHAR(255) NOT NULL DEFAULT 'JOB_POSTING';
ALTER TABLE public.arena_moderation_items ADD COLUMN room_id UUID REFERENCES arena_rooms (id);
ALTER TABLE public.arena_moderation_items ADD COLUMN reporter_user_id UUID REFERENCES arena_users (id);
CREATE INDEX idx_moderation_items_room_id ON public.arena_moderation_items (room_id) WHERE room_id IS NOT NULL;
