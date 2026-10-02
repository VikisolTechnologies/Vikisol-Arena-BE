# Deploy checklist — `feature/admin-account-gaps` → Railway `arena-api`

Written as part of MARATHON-BE step 3 (production-safety self-check). This is a checklist to
read before the founder merges and Railway deploys — it does not deploy anything itself.

## 1. Railway environment variables (names only — no values here)

**Database**
- `DB_URL`, `DB_USERNAME`, `DB_PASSWORD`, `DB_POOL_SIZE` (optional, defaults to 10)
  - `DB_PASSWORD` has **no default** in `application.yml` — startup fails immediately if it's
    missing, in every profile, not just production (ARCHITECT-REVIEW-BE-1 blocker #7).

**Redis**
- `REDIS_URL`

**JWT / sessions**
- `JWT_SECRET`, `JWT_ISSUER` (optional), `JWT_AUDIENCE` (optional), `JWT_EXPIRATION_MS`
  (optional), `JWT_REFRESH_EXPIRATION_MS` (optional)
- `JWT_REQUIRE_REAL_SECRET=true` — **must be set in production**, or `JwtSecretGuard` can't catch
  a deploy that's still signing tokens with the checked-in dev secret.
- `JWT_COOKIE_DOMAIN`, `JWT_COOKIE_SAME_SITE` (optional), `JWT_COOKIE_SECURE` (optional, should be
  `true` in production)
- `SERVICE_TOKEN_SECRET_ARENA` — the Jenny round-trip service-token secret; must match JennySol's
  own copy of the same value exactly (see "JennySol gateway" below).

**File storage / signing**
- `STORAGE_ROOT_DIR`, `STORAGE_PUBLIC_BASE_URL`
- `FILE_SIGNING_SECRET` — **must not be left unset in production.** Unlike `JWT_SECRET`, this one
  doesn't need a separate flag: `FileSigningSecretGuard` (added this pass) fails startup on its
  own dev fallback whenever the active profile isn't `local`.
- `FILE_SIGNED_URL_TTL_MS` (optional)

**Cloudinary** (photo/video uploads)
- `CLOUDINARY_CLOUD_NAME`, `CLOUDINARY_API_KEY`, `CLOUDINARY_API_SECRET`
  - If any are missing and the profile is `local`, uploads fall back to local disk (B12).
  - If any are missing and the profile is **not** `local`, startup only *warns* (does not fail —
    uploads themselves return a clean 400 until configured). Decide before go-live whether that
    should become a hard failure instead; flagging it here rather than changing the behavior
    unasked.

**Mail**
- `RESEND_API_KEY`, `RESEND_FROM`

**SMS / OTP (MSG91)**
- `MSG91_AUTH_KEY`, `MSG91_SENDER_ID`, `MSG91_TEMPLATE_ID`, `MSG91_OTP_VARIABLE_NAME` (optional)

**WhatsApp**
- `WHATSAPP_ACCESS_TOKEN`, `WHATSAPP_PHONE_NUMBER_ID`

**Google sign-in**
- `GOOGLE_CLIENT_ID`

**Microsoft Teams (interview scheduling)**
- `TEAMS_CLIENT_ID`, `TEAMS_CLIENT_SECRET`, `TEAMS_TENANT_ID`, `TEAMS_ORGANIZER_EMAIL`

**JennySol gateway**
- `JENNYSOL_GATEWAY_URL`
- `SERVICE_TOKEN_SECRET_ARENA` (listed above too — it's the credential that makes the gateway
  relationship work; both sides need the same value)
- `OPENAI_API_KEY`, `OPENAI_EMBEDDING_MODEL` (optional) — used for post/job embeddings, not the
  JennySol conversation itself.

**CORS**
- `CORS_ORIGINS` — comma-separated. Must include `https://arena.vikisol.in` for production, and
  whatever preview/staging origins (`preview-arena.vikisol.in` etc.) are still in active use.
  Never include a `localhost` origin in the production value.

**Seeding**
- `SEED_ENABLED=false` — **must be explicitly set in production**, every single deploy, not just
  the first. This was the exact cause of the "mystery demo content" the founder reported locally
  (REPORTS.md, B12): an earlier start left this unset, and the old default was `true`.
  MARATHON-BE step 2 flipped both defaults (the YAML placeholder and the bean's
  `@ConditionalOnProperty`) to `false`, and added a startup guard that fails outright if
  `SEED_ENABLED=true` is ever combined with a non-`local` profile — so forgetting the var now
  fails *safe* (no seeding) instead of failing *open* (seeds demo data). Still set it explicitly;
  don't rely on the new default as the only line of defense.
- `ARENA_SEED_MODE` — the separate, on-demand `DemoContentService` overlay (admin-triggered,
  labeled, removable). Leave unset/`false` in production; this is a founder-facing demo tool, not
  bootstrap seeding.

**Platform admin bootstrap**
- `PLATFORM_ADMIN_EMAIL`, `PLATFORM_ADMIN_PASSWORD` — both blank means no account is created
  (safe default). Set both once, on the very first production start, to create the real platform
  admin; the founder types the password, never commits or logs it. Can be left set on later
  starts — the bootstrap is existence-checked, not re-run blindly.
- `ADMIN_2FA_REQUIRED` (optional, defaults `true`) — leave at the default in production.

**Observability**
- `SENTRY_DSN`, `SENTRY_ENVIRONMENT`, `SENTRY_TRACES_SAMPLE_RATE` (optional)

**Rate limiting** (all optional, sensible defaults already in `application.yml`)
- `RATE_LIMIT_ENABLED`, `RATE_LIMIT_AUTH_PER_MIN`, `RATE_LIMIT_DEFAULT_PER_MIN`,
  `RATE_LIMIT_UPLOAD_PER_MIN`, `RATE_LIMIT_UNLOCK_PER_MIN`, `RATE_LIMIT_MESSAGING_PER_MIN`,
  `RATE_LIMIT_MESSAGING_READ_PER_MIN`, `RATE_LIMIT_POST_CREATION_PER_MIN`,
  `RATE_LIMIT_JOIN_REQUEST_PER_MIN`

**Misc**
- `FRONTEND_URL` — used in outbound email links.
- `PORT` — Railway sets this automatically; don't override.
- `SPRING_PROFILES_ACTIVE` — **must not be `local`** in production (several of this pass's new
  guards, and the existing B12 Cloudinary warning, key off exactly this).

## 2. Memory

**1 GB** per the architect's spec. `Dockerfile`'s `JAVA_OPTS=-XX:MaxRAMPercentage=60
-XX:+ExitOnOutOfMemoryError` already targets this — the JVM heap sizes itself off the
container's actual memory limit (not a hardcoded `-Xmx`), and a real OOM restarts the process
instead of hanging. Set the Railway service's memory limit to 1 GB; no code change needed to
match it.

## 3. Migrations V21–V44

All of them run automatically via Flyway on startup (`FLYWAY_ENABLED` defaults `true`). Every one
below is additive-only (new tables/columns, or an index) unless noted. Each migration file's own
header comment carries the exact rollback SQL — summarized here, not repeated in full.

| # | What it does to existing data | Safe to re-run? |
|---|---|---|
| V21 | Adds missing FK indexes only. No data touched. | Yes — `CREATE INDEX IF NOT EXISTS`. |
| V22 | New tables (intents, interests, availability) + a nullable `photo_url` column. | Yes. |
| V23 | New activity tables (details, waitlist, attendance, feedback), hung off existing ACTIVITY posts. Nothing existing altered. | Yes. |
| V24 | New needs/offers tables (responses, completions), hung off existing ASK/OFFER posts. | Yes. |
| V25 | New career profile tables. Compensation private by default. | Yes. |
| V26 | New posting/application tables (must-haves, screening, assessments) + a HIRED stage. | Yes. |
| V27 | New business-verification table. | Yes. |
| V28 | New COLLAB-post tables (team membership). The paid marketplace (`arena_projects`) is untouched. | Yes. |
| V29 | Adds a WITHDRAWN stage (withdrawing no longer deletes the row) + apply extras, event timeline, recruiter notes. | Yes for the new tables; **rollback** needs WITHDRAWN rows moved/deleted first — not relevant to re-running forward. |
| V30 | Restructures activity details to the flow doc's shape; `kind`/`details_json` become unused (column stays, nullable). Posts gain denormalised price + link-only flag. | Yes. |
| V31 | Adds `cancel_reason`, `edited_at` on posts; widens the status check constraint to include PAUSED. | Yes — constraint is dropped/recreated idempotently. |
| V32 | Adds columns on `arena_need_details`; **maps a few rows** written under an earlier category list (this branch only) to the current list. | Yes — the data-mapping step is a one-time, idempotent `UPDATE ... WHERE` on old values; re-running finds nothing left to map. |
| V33 | Adds columns on `arena_career_profiles` (extra setup fields + a per-field visibility map). | Yes. |
| V34 | Adds posting fields (work mode, experience level, deadline, DRAFT status), typed screening questions, saved jobs, per-must-have interview feedback. | Yes. |
| V35 | Restructures business verification into a PENDING/VERIFIED/REJECTED queue; adds website/GSTIN/CIN/HQ city/logo. | Yes. |
| V36 | Adds project detail fields, per-role skills/hours, milestone checklist, completion contributors. | Yes. |
| V37 | Adds report evidence, notification categories/snooze/preferences, profile visibility, connect requests, dispute-upheld status. | Yes. |
| V38 | New geohash index for nearby discovery. Index-only. | Yes. |
| V39 | Adds `closed_at` on postings (**backfilled** from `updated_at` for already-CLOSED rows) and `verification_grandfathered_at` on companies. | Yes — the backfill `UPDATE` only touches rows where `closed_at IS NULL`, so re-running is a no-op the second time. |
| V40 | Trigram (`pg_trgm`) search indexes. **Schema-qualifies `search_path`** so `gin_trgm_ops` resolves regardless of the connecting role's own search_path; degrades to no-index search (doesn't fail the deploy) if the extension/operator class genuinely isn't available. | Yes — every `CREATE INDEX`/`CREATE EXTENSION` is `IF NOT EXISTS`. |
| V41 | Adds admin/account columns (`suspended_at`, `suspended_until`, `suspension_reason`, `banned_at`, `sessions_revoked_at`, `last_active_at`). | Yes. |
| V42 | Deletes the `agent_autopilot` feature flag row if one was ever created; flips any AUTOPILOT profile to SUPERVISED; drops AUTOPILOT from the allowed values. **This one does delete/change existing rows** (deliberately — founder decision, no autopilot, ever). | Not meaningfully re-runnable in the sense of "undoing" — it's a one-time correction. Re-running finds nothing left to flip (idempotent in effect), but the original AUTOPILOT values are gone for good; that's the intended outcome, not a side effect to guard against. |
| V43 | Adds `reported_user_id` on moderation items (nullable FK, `ON DELETE SET NULL`). | Yes. |
| V44 | **The one with real re-run risk.** Creates `arena_industries` (seeded with the original five keys), drops the three old `*_industry_check` constraints, adds three `NOT VALID` FKs from profiles/companies/postings to it, then validates them (`NOT VALID` + separate `VALIDATE CONSTRAINT` specifically to avoid an `ACCESS EXCLUSIVE` table lock on production). **If any existing row's `industry` value isn't one of the five seeded keys, `VALIDATE CONSTRAINT` fails the migration.** See the pre-deploy SQL check below — this is exactly what it's checking for. | Only safe to re-run if nothing already depends on the FK being in place; in practice Flyway won't re-run an applied migration anyway, so this matters only if `flyway_schema_history` needs manual repair. |

## 4. The two pre-deploy SQL checks

Run both against **production** before this merge, from a read-only connection:

```sql
-- 1. Confirm production hasn't already advanced past where this branch assumes it is.
SELECT version, description, success FROM flyway_schema_history ORDER BY installed_rank DESC LIMIT 5;
```
**Safe result:** the latest successful row is `V20` (or whatever the last migration on production
`main` actually is at merge time) — i.e., production's history **stops** before V21. If it's
already past V21, STOP: this branch's migrations may already be partially or fully applied, or
diverged from what this checklist assumes, and need reconciling by hand before merging.

```sql
-- 2. Confirm no row would fail V44's VALIDATE CONSTRAINT.
SELECT DISTINCT industry FROM arena_candidate_profiles
UNION SELECT DISTINCT industry FROM arena_enterprise_profiles
UNION SELECT DISTINCT industry FROM arena_job_postings;
```
**Safe result:** every value returned is one of `ENGINEERING`, `DESIGN`, `SALES`, `HEALTHCARE`,
`LOGISTICS` (V44's seeded five, case-sensitive). **Any other value — including `NULL`, if any of
these columns can hold one — must be seeded into `arena_industries` first** (see
`/admin/industries`) or V44's `VALIDATE CONSTRAINT` step fails the whole deploy partway through.

## 5. How to roll back

- **Before this PR's migrations run at all:** don't deploy. Nothing to roll back.
- **After deploy, before the founder is satisfied:** Railway redeploy the previous image
  (`main`'s last known-good build). The new migrations (V21–V44) stay applied — every one is
  additive except V42 (drops the retired AUTOPILOT value, intentionally) and V44 (replaces the
  closed industry-list constraint with the new table + FK). Rolling the **application code** back
  to `main` while the **schema** stays on V44 is safe: `main`'s old code never references
  `arena_industries` and keeps reading/writing the same `industry` string column it always did;
  the FK constraint just silently also enforces a superset of what the old CHECK constraint did
  (the five original keys, which is all `main`'s code ever writes).
- **If a specific migration must be undone:** every migration file's own header comment carries
  the exact rollback SQL for that one. Run them in **reverse order** (V44 down to whichever one
  you're rolling back to), not out of order — several (V29, V26) call out their own ordering
  requirements (e.g. WITHDRAWN/HIRED rows must be moved or deleted before their tables can be
  dropped).
- **If V44's `VALIDATE CONSTRAINT` fails mid-deploy:** the `NOT VALID` constraints are already
  added (just unvalidated) and the extra `arena_industries` values are seeded — fix the offending
  `industry` value(s) in place (correct the typo, or add the missing key via
  `/admin/industries`), then re-run `ALTER TABLE ... VALIDATE CONSTRAINT ...` for whichever table
  failed; no need to re-run the whole migration.

## 6. Startup validation (fail fast, not fail open)

These already exist on this branch and run on every single startup, in every profile:

- **`DB_PASSWORD`** — no default at all; a missing value fails Spring's own property binding
  immediately (blocker #7).
- **`JwtSecretGuard`** — fails if `JWT_REQUIRE_REAL_SECRET=true` and `JWT_SECRET` is still the
  checked-in dev fallback, or shorter than 32 characters. Requires the operator to also remember
  to set the flag.
- **`FileSigningSecretGuard`** (added this pass) — fails if the active profile isn't `local` and
  `FILE_SIGNING_SECRET` is still the checked-in dev fallback. Tied directly to the profile, not a
  separate flag — doesn't depend on the operator remembering a second thing.
- **`DataSeeder`'s profile guard** (added this pass) — fails if `SEED_ENABLED=true` and the active
  profile isn't `local`, regardless of how that combination happened.
- **`CloudinaryService`** (B12) — *warns*, doesn't fail, if Cloudinary env vars are absent outside
  `local`. Deliberately a warning today (uploads degrade to a clean error, not a crash); revisit
  if the founder wants this to be a hard failure before launch.

**Not yet unified into one check.** Each of the above is its own small, independently-testable
guard rather than a single "validate everything" class — consistent with how this codebase already
separates concerns (see `JwtSecretGuard` vs `DataSeeder`'s own guard). If a future pass wants one
consolidated startup report instead of N independent `@PostConstruct`/`ApplicationRunner` checks,
that's a refactor, not a new gap — nothing above currently fails silently or fails open.
