# Backend fixes — `cloud/api-hardening`

29 Sep 2026. Branch `cloud/api-hardening`, off `main` at `f89b01c`. Not merged; not deployed.
All tests ran against a throwaway embedded Postgres (zonky, already used by the suite) with the
repo's dummy defaults. No Railway or real database was touched. Dockerfile, `railway.toml`, env
files, CI and deployment config are unchanged.

## Test results

| Run | Test classes | Tests | Failures | Errors |
|---|---|---|---|---|
| Baseline (`main` at `f89b01c`, before any change) | 20 | 88 | 0 | 0 |
| After the first round (PR opened) | 26 | 113 | 0 | 0 |
| After the architect's follow-ups (§6–§8) | 29 | 125 | 0 | 0 |

Every baseline test still passes. No test was deleted or loosened. Two existing tests had call
sites updated because a service method gained a `Pageable` argument and now returns a `Page`
(`AgentApprovalFlowTest`, `AnonymityTest`: `…, PageLimits.firstPage()).getContent()`); their
assertions are unchanged. Command: `mvn test`.

---

## 1. Talent Universe → public profile links 404'd

**Issue.** `GET /enterprise/talent/search` returns `candidate.id` = the **CandidateProfile** id.
`GET /profile/{id}` looked the profile up by **User** id only, so every "view profile" link built
from a search result returned 404 "Candidate not found". Applicants (`candidateId`), the shortlist
and the consent view hand out the same profile id.

**Fix.** `CandidateProfileService.getPublicProfile` resolves the id as a User id first, then as a
CandidateProfile id. The response `id` is always the User id, and follower counts use the User id,
so the payload is identical whichever id the link used. No response shape changed.
(`02b77b0`)

**Proof.** `profile/PublicProfileIdTest`:
- `aTalentUniverseResultLinksToTheCandidatesPublicProfile`: a recruiter runs the real search over
  HTTP, takes `candidate.id` (asserted to be the profile id, not the user id), opens
  `/profile/{that id}` logged out, gets 200 with the user id and name.
- `theUserIdStillOpensTheSameProfile`: both ids return the same user id.
- `anUnknownIdIsStillNotFound`: 404 in the error envelope.

Without the fix the first two fail with 404 (checked by stashing the fix).

## 2. N+1 queries

Hibernate statistics are now on for every app-level test (`EmbeddedPostgresAppTest`).
`performance/QueryCountTest` flushes and clears the persistence context, counts prepared
statements for a call with 2 rows and again with 8–10 rows, and requires the two counts to match.

| List | Statements before (few → many) | Fix |
|---|---|---|
| `FollowService.getFollowers/getFollowing` | 3 → 9 | one `findByUserIdIn` for the page's profiles |
| `BlockService.getMyBlocks` | 3 → 9 | same |
| `ConversationService.getMyConversations` | 6 → 26 | `join fetch` both participants; profiles and tenants batched (`EnterpriseProfileService.mapByUserId`: memberships then founding admins, two IN-queries) |
| `RoomService.getMyRooms` | 7 → 25 | entity graph `room.post`; member counts as one `GROUP BY`; latest message per room in one query |
| `PostService.getJoinRequests` | 4 → 10 | profiles batched in `PostMapper.toResponseList`; `post` fetched with the requests |

After: constant for all five. Shared helper `CandidateProfileRepository.mapByUserId`. The feed,
trending, nearby, comments, room/DM history, talent search and communities were already batched
and were left alone. (`3e76e5e`)

## 3. Unbounded lists → pagination

**Issue.** Nine list endpoints returned every row (followers, following, blocks, rooms, DM
conversations, join requests, communities, my communities, Jenny chat history). Endpoints that
already had `page`/`size` accepted any size (`size=100000`) and turned a negative page into a 400.

**Fix.** `common/dto/PageLimits`: size clamped to 1..100, page ≥ 0, applied to every `page`/`size`
endpoint. The nine lists take optional `page`/`size` (default first 100) and **still return a bare
array**, so the frontend's shape is unchanged. Full details and the limits a user could notice are
in `API-CHANGES.md`. (`192bfeb`)

**Proof.** `performance/ListPaginationTest`: the followers list stays an array, `size=2` and
`page=1&size=2` page it; with 101 followers, `size=100000&page=-3` returns exactly 100;
`/posts/feed` and `/jobs` with `size=100000&page=-1` return 200 and `/jobs` reports `size: 100,
page: 0`; `PageLimits.slice` edge cases.

## 4. Missing indexes

**Issue.** Querying the migrated schema for single-column foreign keys with no index leading on
that column found 16. Six are on real query paths or on the fastest-growing tables.

**Fix.** New migration `V21__missing_fk_indexes.sql` (no applied migration edited), same
`CREATE INDEX IF NOT EXISTS` style as `V3`. Rollback statements are in its header.

| Index | Serves |
|---|---|
| `arena_posts (author_company_id, created_at DESC) WHERE author_company_id IS NOT NULL` | company page posts (guest-reachable) |
| `arena_post_comments (author_user_id, created_at)` | anonymous-reply rate limit on every anonymous comment |
| `arena_post_reactions (user_id)` | "my vote" lookups; FK check on user delete |
| `arena_room_messages (sender_user_id)` | FK check on user delete (demo cleanup) |
| `arena_thread_messages (sender_user_id)` | same |
| `arena_communities (created_by_user_id)` | "communities you started" limit on create |

(`d7e4a55`)

**Proof.** `schema/MissingIndexMigrationTest` checks all six exist after Flyway runs.
`SchemaValidationTest` still passes (migrations + `ddl-auto: validate`).

## 5. One error contract

**Issue.** The error envelope `{"success": false, "message": "...", "data"?}` already existed, but
several paths escaped it: Spring's own request errors (unknown route, wrong method, missing
parameter, wrong content type) are `ServletException`s and fell into the catch-all as **500**; an
oversized upload and any `ResponseStatusException` became 400; a non-constraint database error
became a 400 carrying the driver's message (SQL text); the servlet `/error` page used Spring Boot's
`{timestamp, status, error, path}`; a guest's error dispatch was re-authenticated and came back
401; filter-chain 403s had an empty body.

**Fix.** (`bee56b1`)
- `GlobalExceptionHandler`: one handler for Spring's request errors using each exception's real
  status (404/405/400/415, `ResponseStatusException` status + reason), 413 for uploads, 400 for
  `ConstraintViolationException`, 409 for lock timeouts/deadlocks (`ConcurrencyFailureException`,
  the rows behind join capacity, Jenny approvals and unlock credits are pessimistically locked),
  500 without detail for any other `DataAccessException`.
  A shared `messageFor(status)` keeps wording identical everywhere.
- `ApiErrorController` replaces Boot's `/error` body with the same envelope (status only, never the
  exception message or path).
- `SecurityConfig`: the ERROR dispatch is `permitAll` (it only renders an already-failed request's
  status), and filter-chain 403s write the envelope. Logged in `docs/DECISIONS.md`.

Success responses were not touched. Status changes are listed in `API-CHANGES.md` §4.

**Proof.** `common/ErrorContractTest` asserts status, JSON content type and that the body's keys are
exactly `success`, `message` for: unknown route 404, `PUT /version` 405, `/posts/nearby` without
`lat` 400 "Lat is required", `text/plain` to `POST /posts` 415, the `/error` page for 500 and 429,
plus the existing cases (bad UUID 400, missing profile 404, guest 401, talent on recruiter search
403, bad JSON 400) and the validation field map. Handler-level: DB errors are 500 and don't echo
SQL, lock conflicts are a 409 that doesn't echo SQL, uploads 413, `ResponseStatusException` keeps
409 + reason. On the old code the 404, 405, 400,
415 and `/error` cases fail (checked by stashing the change).

## 6. Provider errors never reach users (architect decision)

**Issue.** Resend, MSG91, WhatsApp, Teams and OpenAI threw `RuntimeException("… returned 401:
<body>")`. Phone sign-in and phone verification did not catch it, so the provider's response came
back as a 400. That response can include the recipient and even the code. OpenAI failures did
the same on a signed-in user's feed. The logs had the same problem:
- callers logged recipients' email addresses;
- the Resend success log printed the subject, which for a sign-in email contains the code;
- the Teams success log printed the meeting join link.

**Fix.**
- **One exception type.** Every provider now throws `ProviderException`. Its message is always a
  short user message chosen by the kind of failure:
  - code (SMS or sign-in email): "We couldn't send the code right now. Please try again in a minute."
  - email: "We couldn't send the email right now. Please try again in a minute."
  - WhatsApp and meeting link: the same pattern.
- **503 response.** `GlobalExceptionHandler` returns that message with a 503.
- **Redacted server log.** `ProviderException.failure(…)` logs the provider's detail once,
  server-side, through `ProviderLogs.redact`. It masks emails, bearer tokens, key-like strings and
  any run of 4+ digits (phones, codes), and truncates to 500 characters. The raw text is not kept
  as the exception's cause.
- **Clean success logs.** Providers no longer log recipients, subjects or join links.
- **Callers log ids.** Callers log user, interview or application ids, never email addresses.
- **Sign-in code email.** A failure now gives the same 503 and message as an SMS code failure.
- **Feed.** The interest embedding is best-effort, like post creation already was: OpenAI down
  means no interest boost, not a failed feed.

Jenny's chat was already safe: a gateway failure is caught and the reply is the fixed copy "The
agent is temporarily unavailable. Your Arena account is still working normally." That exact text
is mandated in `AgentService` (ARENA-DOCUMENT-3), so it was kept rather than reworded.

**Proof.** `integration/ProviderErrorTest`:
- An MSG91 failure whose raw text carries a key, a phone number, the code and an email returns
  503 with the exact short message. None of that text is in the body.
- The server log has provider, kind, `HTTP 401` and the redacted detail, and none of the secrets.
- A failed sign-in code email returns the same message.
- The feed returns 200 while OpenAI throws.
- Every kind's exception message equals its user text, with no cause attached.

`integration/ProviderLogsTest` covers the redaction rules and truncation.

## 7. Capped lists: most recent first (architect decision)

**Issue.** A 100-row cap only helps if page 0 holds the most recent rows. `/rooms` sorted by join
time, so a busy room you joined long ago sank below a silent new one. Same-timestamp rows had no
tiebreak, so a row could move between pages.

**Fix.**
- Followers, following, blocks and Jenny history are newest first. Conversations are sorted by
  latest message.
- `/rooms` is sorted by latest activity (last message, else join time), in one query with the
  post fetched.
- Every time-ordered list breaks ties by id.
- Jenny history pages are selected newest first but read oldest → newest inside the page, because
  the chat renders top to bottom and `AgentApprovalFlowTest` reads the latest message as the last
  element.
- Join requests stay an oldest-first review queue, and communities keep their member-count
  ranking. Neither is time-based.

**Proof.** `performance/ListOrderAndHeadersTest`:
- Followers come back newest first, and page 1 holds the oldest.
- Conversations are sorted by last message.
- `/rooms` puts an older-joined room with a new message ahead of a newer silent room. The old
  join-time order fails this.
- Jenny page 0 holds `m4, m5` of five messages, and page 2 holds `m1`.
- Join requests are oldest first.

## 8. `X-Total-Count` / `X-Has-More` (architect decision)

**Fix.** The nine capped list endpoints return `X-Total-Count` (rows across all pages) and
`X-Has-More` (`true`/`false`). The body stays a bare array. Services return a Spring `Page`, and
`PageLimits.ok(page)` writes the body and the headers. Both headers are in
`Access-Control-Expose-Headers`, so the browser can read them. Documented in `API-CHANGES.md`
§2.1.

**Proof.** `ListOrderAndHeadersTest` checks both headers on followers, conversations, rooms, Jenny
messages, join requests and communities, including `true` on a partial page and `false` on the
last. It also checks that a request from `http://localhost:3000` gets both names in
`Access-Control-Expose-Headers`.

---

## Not fixed, and why

1. **Dev-only noop providers print codes.** With no provider configured, `NoopPhoneOtpProvider` and
   `NoopEmailProvider` log the would-be message, code included, by design, so local sign-in works
   without SMS or email. Production must keep `MSG91_*` and `RESEND_API_KEY` set. A startup
   warning, or refusing the noop providers outside local, is a follow-up.
2. **`IllegalStateException` "not configured / unavailable" is a 400.** e.g. Google sign-in without
   `GOOGLE_CLIENT_ID`, "Agent service is unavailable". A 503 would be more accurate, but the
   frontend may show these messages today, and the Jenny paths are contract-sensitive. Left as is.
3. **Composite `(user_id, created_at)` indexes for follows / room members.** The new paged queries
   sort one user's rows; per-user sets are small and the existing single-column indexes serve the
   filter. Worth adding only if those lists grow.
4. **Ten remaining unindexed FKs** (`closed_by_user_id`, `post_id` on conversations, credit-ledger
   actor, deliverable submitter, membership inviter, three moderation-item columns, rating
   `from_user_id`, room-report reporter). They are on small tables with no query that filters on
   them; indexing them now costs writes for no measured gain.
5. **Frontend docs.** `docs/PROGRESS.md` and `docs/ARENA-CURRENT-STATE.md` in the frontend repo were
   not reachable from this session (only this repository is in scope), so they were not read or
   updated. The frontend can keep linking with `candidate.id`; nothing there needs to change for
   §1 to work.
