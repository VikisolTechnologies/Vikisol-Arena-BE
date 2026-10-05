# Vikisol Arena Backend (arena-api)

Spring Boot 3.3 / Java 21 backend for Vikisol Arena, a talent marketplace product. Implements the
REST API that `arena-web` (currently mock-data-backed) will eventually call over HTTP. Standalone
project - own repo, own package (`com.vikisol.arena`), own database (`vikisol_arena`). No code or
dependency edges to `HRLMS-BE` or `vikisol_one`; conventions were read from `HRLMS-BE` for
consistency only.

## Running it locally (macOS, no Docker)

Two things are required: a local Postgres and a local Redis (`RefreshTokenService`,
`TokenDenylistService`, and `RateLimitFilter` are all Redis-backed — the app will not start
without one). Docker Compose is the other option if Docker is installed; this section covers the
Homebrew path used to run B9's local integration backend.

### 1. One-time setup

```bash
brew install postgresql@16 redis   # skip anything already installed
brew services start postgresql@16
brew services start redis

# Pick your own local-only password here - DB_PASSWORD has no default (ARCHITECT-REVIEW-BE-1
# blocker #7), so whatever you choose, you type it again as an env var in step 2.
psql postgres -c "CREATE ROLE postgres LOGIN SUPERUSER PASSWORD 'type-your-own-local-password-here';"
createdb -U postgres -h localhost vikisol_arena
```

If your local Postgres role/username differs, override `DB_USERNAME` / `DB_PORT` / `DB_NAME`
too instead of changing the role. Redis needs no auth locally — the default
`redis://localhost:6379` (see `REDIS_URL`) just works once `brew services start redis` is up.

### 2. Start the app

```bash
export JAVA_HOME=/opt/homebrew/opt/openjdk@21   # or wherever your JDK 21 lives
DB_PASSWORD="the-same-password-from-step-1" \
SEED_ENABLED=false \
SPRING_PROFILES_ACTIVE=local \
PLATFORM_ADMIN_EMAIL="you@vikisol.dev" \
PLATFORM_ADMIN_PASSWORD="type-your-own-password-here" \
./mvnw spring-boot:run
```

`DB_PASSWORD` is required - there's no built-in default any more, so startup fails loudly instead
of ever silently using one. `SPRING_PROFILES_ACTIVE=local` turns on the local-disk photo-upload
fallback (see "Photo uploads locally" below) - harmless to leave out, but without it photo/post
uploads just report "not set up yet" the same as before.

**`SEED_ENABLED=false` matters every time, not just the first run.** `DataSeeder` only checks
"has anyone already seeded this database" (`EnterpriseProfile` count), not `SEED_ENABLED` itself
at that point - so if the app is ever started even once without `SEED_ENABLED=false` (an IDE run
config with no env vars is the easy way to do this by accident), it seeds ~40 realistic-looking
talent/company/job/post rows that are **not** marked `demo_content = true` the way the separate,
intentional `DemoContentService` overlay is - they're permanently indistinguishable from real
content after that, and setting `SEED_ENABLED=false` afterward only stops it from happening
*again*, it can't undo what already got seeded. If `GET /admin/metrics/launch` or the feed looks
suspiciously populated on a database that should be empty, that's almost certainly what happened -
reset (above) rather than trying to hand-delete the seeded rows.

Starts on `http://localhost:8081`, API base path `/api/v1` (so `http://localhost:8081/api/v1`).
Flyway runs every migration up to the current `V44` automatically on first boot. `SEED_ENABLED=false`
means **no demo data** — the founder wanted a clean prototype on real data, not seeded rows.
CORS already allows `http://localhost:3000` and `http://localhost:3001` by default
(`CORS_ORIGINS`), no extra config needed for the frontend to call this.

Swagger UI: `http://localhost:8081/api/v1/swagger-ui.html`
Health check: `http://localhost:8081/api/v1/actuator/health`

### 3. Reset the database

```bash
dropdb -U postgres -h localhost vikisol_arena && createdb -U postgres -h localhost vikisol_arena
```

Flyway rebuilds the schema from scratch the next time the app starts. Nothing needs to be dropped
in Redis — it only holds refresh tokens, denylist entries, and rate-limit counters, all disposable.

### Photo uploads locally (no Cloudinary)

Post/activity photos normally go browser → Cloudinary directly (`POST /media/upload-signature`,
see `CloudinaryService`'s own comment for why). With no `CLOUDINARY_*` env vars set, that reports
"Photo and video uploads aren't set up yet" — fine for most local work, but blocks verifying the
photo-cover path specifically.

With `SPRING_PROFILES_ACTIVE=local` set (step 2 above) and Cloudinary still unconfigured,
`POST /media/upload-signature` instead points the browser at this server's own
`POST /media/local-upload`, which stores the file on local disk through the same
`FileStorageService` every other upload (CVs, profile photos) already uses, and returns the same
`{"secure_url": "..."}` shape Cloudinary would — arena-web's upload code needs no changes to use
either one. **Images only** (`png`/`jpg`/`jpeg`/`webp`/`gif`) — no video support locally yet.

This fallback is dev-only by design: it's never active unless the `local` profile is explicitly
set, and `CloudinaryService` logs a loud warning at startup if Cloudinary is unconfigured and the
`local` profile *isn't* active either (i.e. a real deployment with Cloudinary missing) - that
should never go unnoticed the way a quiet per-upload 400 could.

### 4. The first platform admin (local, one-time)

There is no seeded platform-admin account when `SEED_ENABLED=false` (the old demo address,
`admin@vikisol.dev`, is permanently disabled — see `DemoAccountLockdown`). Instead, set
`PLATFORM_ADMIN_EMAIL` and `PLATFORM_ADMIN_PASSWORD` as env vars (as in step 2) before the first
boot: `DemoAccountLockdown` (an `ApplicationRunner`) creates that one platform-admin account from
the environment if it doesn't already exist, and never logs the password. Type your own password
directly in the shell — don't commit it or paste it into a chat/PR. Platform admin logins require
TOTP two-factor enrollment on first sign-in (`ADMIN_2FA_REQUIRED`, on by default).

Once created, the account persists across restarts as long as you keep the same Postgres database
(re-set the env vars only if you reset the database per step 3).

### Config / env vars

All in `src/main/resources/application.yml`, same override style as `HRLMS-BE`:

| Var | Default | Purpose |
|---|---|---|
| `DB_PORT` / `DB_NAME` / `DB_USERNAME` | `5432` / `vikisol_arena` / `postgres` | Postgres connection |
| `DB_PASSWORD` | *(required, no default)* | Postgres connection - startup fails without it (ARCHITECT-REVIEW-BE-1 blocker #7) |
| `JWT_SECRET` | local-dev fallback (do not reuse anywhere real) | JWT signing key |
| `JWT_EXPIRATION_MS` | `86400000` (24h) | Access token TTL |
| `CORS_ORIGINS` | `http://localhost:3000,http://localhost:3001` | Allowed frontend origins |
| `STORAGE_ROOT_DIR` | `./uploads` | Local-disk file storage root |
| `SEED_ENABLED` | `true` | Toggle demo data seeding - set `false` for a clean/real-data run |
| `REDIS_URL` | `redis://localhost:6379` | Refresh tokens, token denylist, rate limiting |
| `PLATFORM_ADMIN_EMAIL` / `PLATFORM_ADMIN_PASSWORD` | *(blank)* | One-time bootstrap of the real platform admin on first boot - see "The first platform admin" above |
| `RESEND_API_KEY` | *(blank)* | Resend API key - blank means `NoopEmailProvider` (log-only) stays active, see Integrations below |
| `RESEND_FROM` | `Vikisol Arena <no-reply@arena.vikisol.dev>` | Resend "from" address |
| `WHATSAPP_ACCESS_TOKEN` / `WHATSAPP_PHONE_NUMBER_ID` | *(blank)* | Meta WhatsApp Cloud API creds - blank means `NoopWhatsAppProvider` stays active (not wired at any call site yet either way, see Integrations) |
| `TEAMS_TENANT_ID` / `TEAMS_CLIENT_ID` / `TEAMS_CLIENT_SECRET` / `TEAMS_ORGANIZER_EMAIL` | *(blank)* | Azure AD app-only Graph creds for real Teams meeting links - blank means `NoopMeetingLinkProvider` (placeholder link) stays active |

## What's implemented

Auth (JWT, signup/signin/me) - Candidate profile (get/update skills/consent/autonomy, CV upload) -
Jobs + server-side match scoring - Applications (unified candidate/enterprise entity, see
Decisions) - Interviews (propose/confirm slots, shared notes, structured post-interview feedback
that advances the linked application's stage) - Marketplace (projects, bids, award, milestones
with a real 30/40/30 payment-tranche split, deliverables, two-way ratings) - Enterprise (profile,
postings with plan-based active-posting limits, applicant pipeline, talent search, unlock credits,
shortlist) - Messaging (shared bidirectional conversations) - Notifications (including bulk
mark-all-read) - Activity feed - Local-disk file storage behind a swappable interface - Pagination
+ ETag caching on every list endpoint - Realistic Indian-context seed data - Email/WhatsApp/
meeting-link integration scaffolding (see Integrations below).

That covers every domain named in the brief, in the requested priority order.

## Railway deploy readiness (`arena-api`, `feature/admin-account-gaps`) — not deployed yet

What the Railway service needs before this branch can deploy (the architect reviews PR #2 → #3 →
#4 first; the founder merges; Railway deploys after):

- **Env vars (names only — set real values directly in Railway, never in this repo):**
  `DB_URL` (or the individual `DB_*` parts), `REDIS_URL`, `JWT_SECRET`, `JWT_REQUIRE_REAL_SECRET=true`,
  `FILE_SIGNING_SECRET`, `PLATFORM_ADMIN_EMAIL`, `PLATFORM_ADMIN_PASSWORD`, `SEED_ENABLED=false`,
  `CORS_ORIGINS`, `FRONTEND_URL`, `ARENA_SEED_MODE` (leave unset/`false` in production),
  `SERVICE_TOKEN_SECRET_ARENA` / `JENNYSOL_GATEWAY_URL` (once JennySol is wired),
  `RESEND_API_KEY` / `RESEND_FROM` (once Resend is provisioned), `RAILWAY_GIT_COMMIT_SHA`
  (auto-injected by Railway, not set manually).
- **Memory:** 1 GB service. The Dockerfile already sizes the JVM heap off the container
  (`JAVA_OPTS="-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError"`, ~600 MB heap on 1 GB) — no
  change needed, just confirm the Railway service plan is 1 GB.
- **Migrations:** V38–V44 run automatically on boot (Flyway, `baseline-on-migrate: true`) — same
  as the local run above, no manual migration step on Railway.
- **CORS:** add `https://preview-arena.vikisol.in` to `CORS_ORIGINS` for this branch's preview
  (alongside whatever production origins are already set) — it isn't in the default
  `http://localhost:3000,http://localhost:3001` and must be set explicitly as a Railway env var.

## What's not implemented / deferred

- **No refresh-token flow.** Access tokens are a flat 24h JWT in the `Authorization: Bearer`
  header (see Decisions). Fine for local dev; a real deployment would want short-lived access +
  refresh tokens.
- **No Cloudinary.** `FileStorageService` is an interface with one local-disk implementation
  (`LocalDiskFileStorageService`). Swapping in Cloudinary later is a one-class change - not
  attempted here per the brief (credentials deliberately not available yet).
- **No WebSocket/SSE.** Messaging and activity are plain REST, polled by the client - real-time
  transport is an explicitly later phase per the brief.
- **No integration tests.** Verified via manual end-to-end smoke testing against the running app
  (every module: signup/signin, profile + CV upload, job browse + apply, interview propose/confirm,
  marketplace create/bid/award/milestone/deliverable/rating, enterprise postings/pipeline/talent
  search/unlock/shortlist, messaging). No `@SpringBootTest` suite was written - would be the next
  thing to add before this goes anywhere near production.
- **Skill-name text search** in candidate search (`enterprise/talent/search`) only matches
  title/location at the DB level, not individual skill names (the mock did match skill names) -
  would need a join query against the skills collection table; deferred for time.
- **No payments** - unlock-credit *gating* is real (enforced server-side, returns a clear error at
  zero credits - AUDIT.md flagged this as unverified in the mock; it's now a real, tested check),
  but there's no checkout/payment flow to buy more credits.
- **No live Resend/WhatsApp/Microsoft Graph credentials.** The provider interfaces, Noop
  fallbacks, and real (currently-dormant) implementations all exist (`integration/provider/`) and
  are wired at their real call sites, but none of the three external services have been
  provisioned yet - see "Integrations" below for exactly what's real vs. stubbed and what env vars
  a real deployment needs to set.

## Integrations (Phase 5 scaffolding)

`integration/provider/` holds three provider abstractions, each following the same
interface -> Noop -> real shape (mirrors HRLMS-BE's own `integration/provider/` package,
independently implemented - no shared code):

| Interface | Noop (active today) | Real implementation | Status |
|---|---|---|---|
| `EmailProvider` | `NoopEmailProvider` (logs subject+recipient) | `ResendEmailProvider` (Resend HTTP API) | Real implementation written, untested against a live account - no `RESEND_API_KEY` provisioned |
| `WhatsAppProvider` | `NoopWhatsAppProvider` (logs template+recipient) | `WhatsAppBusinessProvider` (Meta Cloud API) | Real implementation written, untested - no BSP account chosen/provisioned; **not wired at any call site** (see below) |
| `MeetingLinkProvider` | `NoopMeetingLinkProvider` (returns `https://meet.arena.dev/{id}`, same as the old mock) | `TeamsMeetingLinkProvider` (Graph app-only OAuth2) | Real implementation written, untested - no Azure AD app registered for Arena |

`integration/config/IntegrationProviderConfig.java` is the single place that picks real vs. Noop -
each real provider's `isConfigured()` is checked once at startup and the winner becomes the
`@Primary` bean. With zero env vars set (the state of this repo today), every one of the three
resolves to its Noop implementation, so the app runs with no external calls made anywhere.

**Wired call sites:**
- `AuthService.signUp()` - fires a welcome `EmailProvider.sendEmail(...)` after account creation.
- `ApplicationService.advanceStageAsEnterprise()` - fires a stage-change email to the candidate
  when an enterprise moves them through the pipeline (the one place `notifyStageChanged` already
  fired before this change; `advanceStageAsCandidate` still doesn't notify, same as before).
- `InterviewService.confirmSlot()` - calls `MeetingLinkProvider.createMeetingLink(...)` to populate
  `Interview.meetingLink` (a field that didn't exist on the entity before this change - added since
  arena-web's mock/`types.ts` already has `Interview.meetingLink` and generates the exact
  `https://meet.arena.dev/{id}` placeholder that `NoopMeetingLinkProvider` now also returns, so this
  is a non-breaking, behavior-preserving addition), then fires a confirmation email with the join
  link.

Every one of these calls is wrapped in try/catch with a `log.warn` on failure - a notification
failure never fails the underlying business operation (signup/stage-change/interview-confirm all
still succeed even if the email/meeting-link call throws). Confirmed by reading HRLMS-BE's own
`EmailService` send methods, which follow the identical catch-and-log-don't-propagate pattern.

**WhatsApp is deliberately not wired at any call site.** `WhatsAppProvider`/
`NoopWhatsAppProvider`/`WhatsAppBusinessProvider` are fully built and ready, but there is no
phone-number field anywhere in Arena's domain model (`User`, `CandidateProfile`,
`EnterpriseProfile`) to send to - wiring a call site would mean fabricating contact data. Add a
`phone` field to `CandidateProfile` (and thread it through the profile DTOs/seed data) before
wiring this in, most naturally alongside the same `advanceStageAsEnterprise` stage-change call
site email fires from today.

## Decisions worth knowing about

- **Unified `Job`/`JobPosting` and `Application`/`Applicant`.** AUDIT.md explicitly called out that
  the mock's enterprise-side `Applicant` and candidate-side `Application` were two independently-
  seeded parallel lists - a real data-coherence bug (a stage change on one side wouldn't reflect on
  the other). Collapsed into one `Application` entity with two DTOs/views
  (`applications/dto/ApplicationResponse` for candidates, `enterprise/dto/ApplicantResponse` for
  enterprises). The same duplication risk existed between `Job` (candidate browse) and `JobPosting`
  (enterprise management) in `types.ts`, even though AUDIT.md didn't name it explicitly - collapsed
  the same way into one `JobPosting` entity (`jobs/entity/JobPosting.java`).
- **Single scoring service.** `matching/ScoringService.java` is the one place career-health and
  match-percentage numbers are computed - used by profile, jobs, marketplace bids, and enterprise
  talent search alike, per AUDIT.md's "no single source of truth for match/health scores" finding.
- **Milestones/deliverables/ratings are new, real data models**, not UI-only state - AUDIT.md noted
  the mock had no `Milestone`/`Rating` type anywhere despite the product mission requiring a full
  bid -> award -> milestone -> deliverable -> completion -> two-way-rating lifecycle. Built as
  first-class entities (`marketplace/entity/{Milestone,Deliverable,Rating}.java`) with a real state
  machine, not a checkbox. Verified end-to-end manually (award -> submit each milestone's
  deliverable -> accept each -> project auto-closes on the last acceptance -> both sides rate).
- **Bearer-token JWT, not HttpOnly cookies.** `HRLMS-BE` uses HttpOnly cookies + a CSRF filter,
  which is the more defensible choice for a browser app but adds real infrastructure (cookie
  service, CSRF token endpoint, SameSite/domain config). For a fresh API with no existing
  cookie/CSRF plumbing, a standard `Authorization: Bearer <token>` header is simpler and still
  fine for this phase; flagging so it isn't assumed to be an oversight.
- **Bid status folded into `Bid` itself.** The mock tracked bid status (`pending/shortlisted/won/
  lost`) in a *second*, separately-written localStorage list (`MyBidRecord` in `myBids.ts`) instead
  of on the `Bid` record itself - the exact "two parallel records" pattern AUDIT.md flagged for
  applications. Folded into one `status` column on `Bid` instead.
- **Messaging is a real shared, bidirectional conversation** (one `Conversation` row per user pair,
  visible to both sides, with per-side read tracking), not one localStorage list per browser like
  the mock. This meant the `getOrCreateConversation(participantId, ...)` call needed a real
  account id to route to; the request now takes `participantUserId` (an actual `arena_users.id`)
  instead of the mock's loosely-typed display id. The frontend's future HTTP adapter will need to
  resolve a real user id before starting a conversation (e.g. from a job posting's enterprise
  owner, or a search result's candidate).
- **`vikisol_arena` local DB already had unrelated data on this machine** (see "Running it
  locally" above) - handled via table prefixing rather than a destructive drop.
- **CV upload is new** (not in the mock's `types.ts` contract at all - AUDIT.md flagged "no real
  CV artifact anywhere" as a gap). Added `POST /profile/me/cv` (multipart) storing via
  `FileStorageService`, plus `cvUrl`/`cvFileName` on `CandidateProfileResponse` and a small
  career-health bonus for having one - extra fields beyond the mock's `CandidateProfile` type,
  additive/harmless for a future frontend adapter that only reads the fields it knows about.
- **Seed data uses a fixed random seed (42)** for a stable-looking demo dataset across restarts on
  a fresh database, mirroring `arena-web`'s own `mulberry32` PRNG choice in `mock/seed.ts`.
- **Interview feedback is embedded on `Interview`, not a separate table** (`InterviewFeedback` is
  a JPA `@Embeddable`, unlike `InterviewSlot` which is a full related entity) - it's a 0-or-1 value
  object, not a list, so no separate table/repository was warranted. Submitting feedback
  (`POST /interviews/{id}/feedback`, enterprise-only) both completes the interview and, in the same
  transaction, advances the linked `Application`'s stage via the existing
  `ApplicationService.advanceStageAsEnterprise()` (advance -> offer, reject -> rejected, hold ->
  stays at interview) - field-for-field mirror of `submitInterviewFeedback()` in arena-web's
  `interviews.ts`. `PUT /interviews/{id}/notes` is open to either participant, matching
  arena-web's `InterviewRoom.tsx` (the notes textarea isn't feedback-gated, only "End & give
  feedback" is).
- **Enterprise posting limits are enforced server-side** (`JobPostingService.createPosting()`),
  mirroring arena-web's `POSTING_LIMITS` (`plan.ts`) exactly: free plan 1 active posting, pro 10,
  enterprise unlimited. "Active" = anything not closed (open or paused).
- **Milestone tranches now carry a real `amount`.** `ProjectService.award()`'s milestone labels
  were changed from 4 generic placeholders to the exact 3 arena-web uses ("Kickoff & plan" /
  "Midpoint delivery" / "Final delivery") with the same 30/40/30 split of the awarded bid - a
  30/40/30 split isn't well-defined against an arbitrary 4th milestone, so this aligns the count as
  well as the field. `DataSeeder`'s demo award uses the same split so seeded data stays consistent
  with a real award.

## API surface (all under `/api/v1`)

```
POST   /auth/signup, /auth/signin, /auth/signout        GET /auth/me
GET    /profile/me                                       PUT /profile/me/{skills,consent,autonomy}
POST   /profile/me/cv
GET    /jobs, /jobs/{id}
GET    /applications                                      POST /applications
GET    /applications/exists?jobId=                        DELETE /applications/{id}
PUT    /applications/{id}/stage
GET    /interviews/by-application/{id}                     POST /interviews/propose/{applicationId}
PUT    /interviews/{id}/confirm                             PUT  /interviews/{id}/notes
POST   /interviews/{id}/feedback (enterprise-only)
GET    /marketplace/projects, /marketplace/projects/{id}   POST /marketplace/projects
GET    /marketplace/my-projects, /marketplace/my-bids
POST   /marketplace/projects/{id}/bids                     POST /marketplace/projects/{id}/award
POST   /marketplace/milestones/{id}/deliverables
PUT    /marketplace/milestones/{id}/{accept,reject}
POST   /marketplace/projects/{id}/ratings
GET    /enterprise/profile/me                               PUT /enterprise/profile/me
GET    /enterprise/postings                                 POST /enterprise/postings
PUT    /enterprise/postings/{id}/status
GET    /enterprise/postings/{id}/applicants                 PUT /enterprise/applicants/{id}/stage
GET    /enterprise/talent/search, /enterprise/talent/{id}   POST /enterprise/talent/{id}/unlock
GET    /enterprise/shortlist                                POST /enterprise/shortlist/{candidateId}/toggle
GET    /messages/conversations                              POST /messages/conversations
GET    /messages/conversations/{id}/messages                POST /messages/conversations/{id}/messages
GET    /notifications                                       PUT /notifications/{id}/read
PUT    /notifications/read-all
GET    /activity
GET    /files/{module}/{entityId}/{documentType}/{fileName}
```

All list endpoints take `page`/`size` and return `{ content, page, size, totalElements,
totalPages, last }`. Every GET response carries a weak ETag (see `config/WebConfig.java`) so
repeated identical fetches can 304 instead of re-transferring payloads.
