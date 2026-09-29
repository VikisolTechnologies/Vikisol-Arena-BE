-- Business verification (G27-G28, see API-CHANGES.md): a company proves it controls its website's
-- domain by confirming a code sent to a work email at that domain. Additive only.
--
-- Rollback:
--   DROP TABLE IF EXISTS public.arena_business_verifications;

CREATE TABLE IF NOT EXISTS public.arena_business_verifications (
    id                   UUID PRIMARY KEY,
    created_at           TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    updated_at           TIMESTAMP(6) WITH TIME ZONE NOT NULL,
    demo_content         BOOLEAN NOT NULL DEFAULT false,
    tenant_id            UUID NOT NULL REFERENCES public.arena_enterprise_profiles (id),
    legal_name           VARCHAR(200) NOT NULL,
    website              VARCHAR(255) NOT NULL,
    domain               VARCHAR(255) NOT NULL,
    work_email           VARCHAR(255) NOT NULL,
    submitter_role       VARCHAR(32) NOT NULL,
    status               VARCHAR(16) NOT NULL DEFAULT 'PENDING',
    code_hash            VARCHAR(100),
    code_expires_at      TIMESTAMP(6) WITH TIME ZONE,
    attempts             INT NOT NULL DEFAULT 0,
    last_sent_at         TIMESTAMP(6) WITH TIME ZONE,
    verified_at          TIMESTAMP(6) WITH TIME ZONE,
    verified_by_user_id  UUID REFERENCES public.arena_users (id),
    CONSTRAINT uk_business_verifications_tenant UNIQUE (tenant_id),
    CONSTRAINT ck_business_verifications_status CHECK (status IN ('PENDING', 'VERIFIED'))
);
