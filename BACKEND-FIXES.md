# Backend fixes — `cloud/api-hardening`

29 Sep 2026. Branch `cloud/api-hardening`, off `main` at `f89b01c`. Not merged; not deployed.
All tests ran against a throwaway embedded Postgres (zonky, already used by the suite) with the
repo's dummy defaults. No Railway or real database was touched. Dockerfile, `railway.toml`, env
files, CI and deployment config are unchanged.

## Test results

| Run | Test classes | Tests | Failures | Errors |
|---|---|---|---|---|
| Baseline (`main` at `f89b01c`, before any change) | 20 | 88 | 0 | 0 |
| After all changes | 26 | 112 | 0 | 0 |

Every baseline test still passes. No test was deleted or loosened. Three existing tests had one
call site updated because a service method gained a `Pageable` argument
(`AgentApprovalFlowTest`, `AnonymityTest`: `…, PageLimits.firstPage()`); their assertions are
unchanged. Command: `mvn test`.

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
  `ConstraintViolationException`, 500 without detail for `DataAccessException`.
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
SQL, uploads 413, `ResponseStatusException` keeps 409 + reason. On the old code the 404, 405, 400,
415 and `/error` cases fail (checked by stashing the change).

---

## Not fixed, and why

1. **Third-party error bodies can reach the client.** Integration providers (Resend, MSG91,
   WhatsApp, Teams, OpenAI) throw `RuntimeException("… returned 401: <body>")`, which
   `handleRuntime` returns as a 400 with that message. Changing it means deciding what the user
   should see when email/OTP delivery fails (a 502 and a generic message is the recommendation);
   that is a UX decision in several flows, so it is left for a separate change.
2. **`IllegalStateException` "not configured / unavailable" is a 400.** e.g. Google sign-in without
   `GOOGLE_CLIENT_ID`, "Agent service is unavailable". A 503 would be more accurate, but the
   frontend may show these messages today, and the Jenny paths are contract-sensitive. Left as is.
3. **Paging metadata for the bare-array lists.** Needs the `PagedResponse` shape change proposed in
   `API-CHANGES.md` §1. Until the frontend adopts it, a client asks for the next page and stops on
   an empty or short page.
4. **Composite `(user_id, created_at)` indexes for follows / room members.** The new paged queries
   sort one user's rows; per-user sets are small and the existing single-column indexes serve the
   filter. Worth adding only if those lists grow.
5. **Ten remaining unindexed FKs** (`closed_by_user_id`, `post_id` on conversations, credit-ledger
   actor, deliverable submitter, membership inviter, three moderation-item columns, rating
   `from_user_id`, room-report reporter). They are on small tables with no query that filters on
   them; indexing them now costs writes for no measured gain.
6. **Frontend docs.** `docs/PROGRESS.md` and `docs/ARENA-CURRENT-STATE.md` in the frontend repo were
   not reachable from this session (only this repository is in scope), so they were not read or
   updated. The frontend can keep linking with `candidate.id`; nothing there needs to change for
   §1 to work.
