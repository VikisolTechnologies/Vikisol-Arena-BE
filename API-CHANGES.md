# API changes — `cloud/api-hardening`

29 Sep 2026. **No successful response changed shape.** Every JSON body the frontend or the Jenny
gateway reads on success is byte-for-byte the same shape as on `main`. This file lists what
did change (request parameters, limits, error statuses), and the shape changes that would help
but were **not** made.

## 1. Shape changes proposed, not made

| Endpoint(s) | Proposal | Why it wasn't done |
|---|---|---|
| `GET /follows/me/followers`, `/follows/me/following`, `/blocks/me`, `/rooms`, `/messages/conversations`, `/posts/{id}/joins`, `/communities`, `/communities/mine`, `/agent/conversations/{id}/messages` | Return `PagedResponse` (`content`, `page`, `size`, `totalElements`, `totalPages`, `last`) instead of a bare array, so a client knows whether more rows exist. | `data` would change from an array to an object. The frontend (and Jenny, for `/communities`) read these as arrays. They take `page`/`size` now and stay arrays; see §2. |
| `GET /enterprise/talent/search`, `GET /enterprise/talent/{id}`, applicants, shortlist | Add `userId` next to `candidate.id` so links can use the canonical User id. | Adds a field to a shared response. Instead `GET /profile/{id}` now accepts either id (BACKEND-FIXES.md §1). |
| Feed-style lists (`/posts/feed`, `/posts/trending`, `/posts/nearby`, `/feed`, `/discuss/threads`) | Same `PagedResponse` proposal. | Same reason. They were already bounded by `size` or a fixed window. |

## 2. New optional request parameters (non-breaking)

These list endpoints used to return **every** row. They now accept `page` (default `0`) and `size`
(default `100`). With no parameters they return the first 100 rows, in the same order as before.

| Endpoint | Order |
|---|---|
| `GET /follows/me/followers`, `GET /follows/me/following` | newest follow first |
| `GET /blocks/me` | newest block first |
| `GET /rooms` | newest membership first |
| `GET /messages/conversations` | most recent message first |
| `GET /posts/{id}/joins` | oldest request first |
| `GET /communities`, `GET /communities/mine` | unchanged ranking, then cut |
| `GET /agent/conversations/{id}/messages` | page 0 = the most recent 100, oldest-first within the page (same as room and DM history) |

**Behaviour a client could notice:** a user with more than 100 followers, rooms, conversations,
blocks or join requests on one post, more than 100 communities in the list, or more than 100 Jenny
messages in one conversation, now sees the first 100 unless they ask for the next page. Room and DM
message history was already capped at 100 (unchanged).

## 3. Limits on existing `page` / `size`

Every endpoint that already took `page`/`size` (or builds a `PageRequest` from them) now goes
through `PageLimits`: `size` is clamped to `1..100`, a negative `page` reads as `0`.
Before, `size=100000` was honoured and a negative page was a 400. `GET /search?limit=` and
`/discuss/threads?size=` already had caps (50 and 100) and keep them.

## 4. Error statuses (shape unchanged: `{"success": false, "message": "..."}`)

| Case | Before | After |
|---|---|---|
| Unknown route (signed in) | 500 "Something went wrong…" | 404 "Not found" |
| Wrong HTTP method | 500 | 405 |
| Missing required query parameter / header / multipart part | 500 | 400 "`<Field>` is required" |
| Unsupported `Content-Type` | 500 | 415 |
| Upload over 10 MB | 400 with Spring's internal message | 413 |
| `ResponseStatusException` | 400 | its own status and reason |
| Database failure other than a constraint violation | 400 carrying the driver message (SQL text) | 500 generic message |
| Anything rendered by the servlet `/error` page | Spring Boot's `{timestamp, status, error, path}` | `{success, message}` with the same status |
| Guest request that errors after security (e.g. inside a filter) | 401 (the error dispatch was re-authenticated) | the real status |
| 403 decided by the security filter chain | empty body | `{"success": false, "message": "Access denied"}` |

Unchanged: 400 validation errors (still carry the field map in `data`), 400 bad JSON, 400 bad UUID,
401 from the entry point, 403 from method security, 404 `ResourceNotFoundException`, 409 constraint
violations, 429 from the rate limiter, and every Jenny service-token 403.
