# API changes — `cloud/api-hardening`

29 Sep 2026. **No successful response changed shape.** Every JSON body the frontend or the Jenny
gateway reads on success is byte-for-byte the same shape as on `main`. This file lists what
did change (request parameters, limits, error statuses), and the shape changes that would help
but were **not** made.

## 1. Shape changes proposed, not made

| Endpoint(s) | Proposal | Why it wasn't done |
|---|---|---|
| `GET /follows/me/followers`, `/follows/me/following`, `/blocks/me`, `/rooms`, `/messages/conversations`, `/posts/{id}/joins`, `/communities`, `/communities/mine`, `/agent/conversations/{id}/messages` | Return `PagedResponse` (`content`, `page`, `size`, `totalElements`, `totalPages`, `last`) instead of a bare array, so a client knows whether more rows exist. | Rejected by the architect: `data` would change from an array to an object, and the frontend (and Jenny, for `/communities`) read these as arrays. The same information is in response headers instead; see §2.1. |
| `GET /enterprise/talent/search`, `GET /enterprise/talent/{id}`, applicants, shortlist | Add `userId` next to `candidate.id` so links can use the canonical User id. | Adds a field to a shared response. Instead `GET /profile/{id}` now accepts either id (BACKEND-FIXES.md §1). |
| Feed-style lists (`/posts/feed`, `/posts/trending`, `/posts/nearby`, `/feed`, `/discuss/threads`) | Same `PagedResponse` proposal. | Same reason. They were already bounded by `size` or a fixed window. |

## 2. New optional request parameters (non-breaking)

These list endpoints used to return **every** row. They now accept `page` (default `0`) and `size`
(default `100`). Time-ordered lists put the **most recent rows on page 0**, so the cap never hides
recent activity. Ties on the timestamp are broken by id, so a row never appears on two pages.

| Endpoint | Order | Same as `main`? |
|---|---|---|
| `GET /follows/me/followers`, `GET /follows/me/following` | newest follow first | yes |
| `GET /blocks/me` | newest block first | yes |
| `GET /messages/conversations` | most recent message first | yes |
| `GET /rooms` | most recent activity first: the latest message, or when you joined if nobody has written | **no**: `main` sorted by when you joined, so a busy old room sank below a silent new one |
| `GET /agent/conversations/{id}/messages` | page 0 = the most recent messages; **inside a page they read oldest → newest** | **no**: `main` returned the whole history oldest → newest. The first page is now the latest 100, still in reading order, because the chat renders top to bottom (and `AgentApprovalFlowTest` reads the latest message as the last element). Older messages: `page=1`, `2`, … |
| `GET /posts/{id}/joins` | oldest request first (a review queue: the host answers people in the order they asked) | yes |
| `GET /communities`, `GET /communities/mine` | most members first / by name (not time-based) | yes |

### 2.1 Paging headers: `X-Total-Count` and `X-Has-More`

Every endpoint in the table above now sends two response headers. The body is unchanged: `data`
is still the bare array of the requested page.

| Header | Value | Example |
|---|---|---|
| `X-Total-Count` | total rows across all pages, as a decimal integer | `X-Total-Count: 137` |
| `X-Has-More` | `true` if a later page exists, otherwise `false` | `X-Has-More: true` |

- Both headers are listed in `Access-Control-Expose-Headers`, so a browser `fetch()` from an
  allowed origin can read them (`response.headers.get("X-Total-Count")`).
- To load the next page, ask for `page + 1` while `X-Has-More` is `true`.
- The feed-style lists (`/posts/feed`, `/posts/trending`, `/posts/nearby`, `/feed`,
  `/discuss/threads`) do **not** send them. They rank a window in memory and have no cheap exact
  total. Endpoints that already return `PagedResponse` carry the same data in the body.

**Behaviour a client could notice:** a user with more than 100 followers, rooms, conversations,
blocks or join requests on one post, more than 100 communities in the list, or more than 100 Jenny
messages in one conversation, now sees the first 100 unless they ask for the next page, and
`X-Has-More: true` tells them there is one. Room and DM message history was already capped at the
latest 100 (unchanged).

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
| Email / SMS / WhatsApp / Teams / OpenAI provider failed | 400 carrying the provider's own text (could include the recipient, or the code) | 503 with a short message, e.g. "We couldn't send the code right now. Please try again in a minute." |
| Sign-in code email failed | 400 "Could not send the code right now - please try again" | 503, same message as an SMS code failure |
| OpenAI down while building a signed-in user's feed | 400 with OpenAI's error text | 200: the feed ranks without the interest boost |
| Lock timeout / deadlock on a locked row | 400 carrying the driver message | 409 "Someone else changed this at the same moment. Please try again." |
| Database failure other than a constraint violation or lock conflict | 400 carrying the driver message (SQL text) | 500 generic message |
| Anything rendered by the servlet `/error` page | Spring Boot's `{timestamp, status, error, path}` | `{success, message}` with the same status |
| Guest request that errors after security (e.g. inside a filter) | 401 (the error dispatch was re-authenticated) | the real status |
| 403 decided by the security filter chain | empty body | `{"success": false, "message": "Access denied"}` |

Unchanged: 400 validation errors (still carry the field map in `data`), 400 bad JSON, 400 bad UUID,
401 from the entry point, 403 from method security, 404 `ResourceNotFoundException`, 409 constraint
violations, 429 from the rate limiter, and every Jenny service-token 403.
