# Vikisol Arena Backend (arena-api)

Spring Boot 3.3 / Java 21 backend for Vikisol Arena, a talent marketplace product. Implements the
REST API that `arena-web` (currently mock-data-backed) will eventually call over HTTP. Standalone
project - own repo, own package (`com.vikisol.arena`), own database (`vikisol_arena`). No code or
dependency edges to `HRLMS-BE` or `vikisol_one`; conventions were read from `HRLMS-BE` for
consistency only.

## Running it locally

### 1. Postgres

You need a local PostgreSQL server reachable at `localhost:5432`. On this machine, PostgreSQL 16
is already installed at `C:\Program Files\PostgreSQL\16` and runs as the Windows service
`postgresql-x64-16`. On a fresh machine:

1. Install PostgreSQL 16+ (the Windows installer from postgresql.org, or `choco install postgresql`).
2. Make sure the service is running (`Get-Service postgresql*` in PowerShell).
3. Create the database and set a password matching the app's default (or override via env vars -
   see below):
   ```
   psql -U postgres -c "CREATE DATABASE vikisol_arena;"
   ```
   Default expected credentials (same convention as `HRLMS-BE`): user `postgres`, password
   `Welcome@12345#`. Override with `DB_USERNAME` / `DB_PASSWORD` / `DB_PORT` / `DB_NAME` env vars
   if your local instance differs.

**Note on this machine specifically:** the local `vikisol_arena` database already contained 13
unrelated tables from a prior, different project (an assessment/quiz platform - `questions`,
`professionals`, `assessment_attempts`, etc. - nothing related to Arena's talent-marketplace
domain). Rather than dropping that database, every table this app owns is prefixed `arena_`
(`arena_users`, `arena_jobs`, `arena_projects`, ...) so `ddl-auto: update` can never collide with
or touch that pre-existing data. A full `pg_dump` backup of the pre-existing data was taken before
any schema changes, in case it turns out to matter to something else. If you want to start from a
truly clean database, drop and recreate `vikisol_arena` yourself; the app doesn't require it.

### 2. Run the app

```
./mvnw spring-boot:run
```

Starts on `http://localhost:8081`, API base path `/api/v1`. First run seeds the database with
realistic demo data automatically (see below) - subsequent runs skip seeding once `arena_users`
has any rows. Disable seeding with `SEED_ENABLED=false`.

Swagger UI: `http://localhost:8081/api/v1/swagger-ui.html`
Health check: `http://localhost:8081/api/v1/actuator/health`

### 3. Demo logins (created by the seeder)

| Role | Email | Password |
|---|---|---|
| Talent | `demo.talent@vikisol.dev` | `Demo@12345` |
| Enterprise | `demo.enterprise@vikisol.dev` | `Demo@12345` |

Plus 9 more seeded companies and 39 more seeded candidates (all password `Demo@12345`, emails
`candidate{N}@example.com`) so search/browse/pipeline screens aren't empty.

### Config / env vars

All in `src/main/resources/application.yml`, same override style as `HRLMS-BE`:

| Var | Default | Purpose |
|---|---|---|
| `DB_PORT` / `DB_NAME` / `DB_USERNAME` / `DB_PASSWORD` | `5432` / `vikisol_arena` / `postgres` / `Welcome@12345#` | Postgres connection |
| `JWT_SECRET` | local-dev fallback (do not reuse anywhere real) | JWT signing key |
| `JWT_EXPIRATION_MS` | `86400000` (24h) | Access token TTL |
| `CORS_ORIGINS` | `http://localhost:3000,http://localhost:3001` | Allowed frontend origins |
| `STORAGE_ROOT_DIR` | `./uploads` | Local-disk file storage root |
| `SEED_ENABLED` | `true` | Toggle demo data seeding |
| `RESEND_API_KEY` | *(blank)* | Resend API key - blank means `NoopEmailProvider` (log-only) stays active, see Integrations below |
| `RESEND_FROM` | `Vikisol Arena <no-reply@arena.vikisol.dev>` | Resend "from" address |
| `WHATSAPP_ACCESS_TOKEN` / `WHATSAPP_PHONE_NUMBER_ID` | *(blank)* | Meta WhatsApp Cloud API creds - blank means `NoopWhatsAppProvider` stays active (not wired at any call site yet either way, see Integrations) |
| `TEAMS_TENANT_ID` / `TEAMS_CLIENT_ID` / `TEAMS_CLIENT_SECRET` / `TEAMS_ORGANIZER_EMAIL` | *(blank)* | Azure AD app-only Graph creds for real Teams meeting links - blank means `NoopMeetingLinkProvider` (placeholder link) stays active |

## What's implemented

Auth (JWT, signup/signin/me) - Candidate profile (get/update skills/consent/autonomy, CV upload) -
Jobs + server-side match scoring - Applications (unified candidate/enterprise entity, see
Decisions) - Interviews (propose/confirm slots) - Marketplace (projects, bids, award, milestones,
deliverables, two-way ratings) - Enterprise (profile, postings, applicant pipeline, talent search,
unlock credits, shortlist) - Messaging (shared bidirectional conversations) - Notifications -
Activity feed - Local-disk file storage behind a swappable interface - Pagination + ETag caching
on every list endpoint - Realistic Indian-context seed data - Email/WhatsApp/meeting-link
integration scaffolding (see Integrations below).

That covers every domain named in the brief, in the requested priority order.

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
- **No structured interview scorecards / employer feedback** beyond stage transitions - matches
  what AUDIT.md flagged as missing in the mock; not built here either (out of scope for this pass).
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
PUT    /interviews/{id}/confirm
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
GET    /activity
GET    /files/{module}/{entityId}/{documentType}/{fileName}
```

All list endpoints take `page`/`size` and return `{ content, page, size, totalElements,
totalPages, last }`. Every GET response carries a weak ETag (see `config/WebConfig.java`) so
repeated identical fetches can 304 instead of re-transferring payloads.
