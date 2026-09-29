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

---

# Backend for the new frontend — `feature/be-fe-gaps`

Stacked on `cloud/api-hardening`. **Every change here is additive:** new endpoints, new tables,
new optional fields. Existing endpoints keep their paths, request bodies and response fields. The
one behaviour change on an existing endpoint is marked **⚠** where it appears.

**Gap numbers:**
- **1–6** are the rows in the frontend's `docs/FE-API-GAPS.md`.
- **G7 and up** are new. They come from `docs/design/BPLUS-SCREENS.md` and the FE `ARENA-MISSION.md`,
  because the `ARENA-APP-FLOW.md` the brief names doesn't exist in the frontend repo. The frontend
  should copy these rows into `FE-API-GAPS.md`.

All bodies use the usual envelope: `{ "success": true, "data": … }`, and errors are
`{ "success": false, "message": "…" }`.

**Protected attributes:** free text that one person writes for others to meet or answer is checked
by `ProtectedAttributes`:
- activity details;
- host questions;
- must-haves;
- screening questions.

It refuses wording about age, gender, marital status, religion, caste or disability with a 400:
"Arena doesn't allow asking about or filtering by … Please rephrase …". There is no such field or
filter anywhere in the API.

## Profile basics (onboarding) — gaps 1–5

All endpoints need a talent session; they only ever touch the caller's own profile.

| Gap | Endpoint | Body | Returns |
|---|---|---|---|
| 1 | `PUT /profile/me/intents` | `{ "intents": ["activities","meet","ask","offer","job","hire","projects","explore"] }` (any subset) | `ProfileBasics` |
| 2 | `PUT /profile/me/interests` | `{ "interests": ["Badminton", …] }`. Up to 20, each ≤30 chars, trimmed, de-duplicated ignoring case | `ProfileBasics` |
| 3 | `POST /profile/me/photo` | multipart `file` (PNG, JPG or WebP, ≤10 MB) | `ProfileBasics` with a signed `photoUrl` |
| 3 | `DELETE /profile/me/photo` | none | `ProfileBasics` without `photoUrl` |
| 4, 5 | `PATCH /profile/me` | `{ name?, title?, bio? (≤160), availability? ("weekdays"\|"weekends"\|"evenings")[] }`. Only the fields sent change | `ProfileBasics` |
| 1–5 | `GET /profile/me/basics` | none | `ProfileBasics` |

`ProfileBasics` = `{ name, title, bio, photoUrl, intents[], interests[], availability[] }`.

- Intents are **self-only**; they appear in no other response.
- `GET /profile/{id}` (public profile) gains three **added** fields: `photoUrl`, `interests[]` and
  `availability[]`.
- Account erasure clears all of them.

**Passwords (gap logged by the frontend):** `POST /auth/signup`, `POST /auth/reset-password` and
`POST /auth/change-password` now need **at least 8 characters**. The 400 is
`data.password` / `data.newPassword`: "must be at least 8 characters". Sign-in is unchanged, so an
existing shorter password still works until it is changed.

## Activities — G7 to G13

`{id}` is the id of an `ACTIVITY` post (created as today with `POST /posts`). A non-activity,
anonymous or removed post answers 404. Only the host (the post's author) can change the activity;
only talent can host or join.

| Gap | Endpoint | Who | Body / notes |
|---|---|---|---|
| G7 | `GET /activities/kinds` | anyone | `{ kind: [type-specific keys] }` catalogue for the form |
| G7 | `GET /activities/{id}` | anyone (guest too) | `Activity` below; `viewer` only when signed in |
| G7 | `PUT /activities/{id}/details` | host | `{ kind?, details?: { key: value }, waitlistEnabled? }`. `kind` is required the first time. Keys: common `level, cost, bring, accessibility, language`, plus per kind (`sport`: sport, format, equipment; `fitness`: activity, pace, distance; `study`: subject, format; `meetup`: theme; `workshop`: topic, materials; `collaboration`: skillsNeeded, commitment; `volunteer`: cause, requirements). Values ≤200 chars, up to 12 keys; an empty value removes the key |
| G13 | `POST /activities/{id}/cover` | host | multipart `file` (PNG/JPG/WebP). Needs `details` first |
| G13 | `DELETE /activities/{id}/cover` | host | |
| G8 | `PUT /activities/{id}/questions` | host | `{ questions: [{ text (≤200), required? (default true) }] }`, at most 3. Refused once anyone has answered |
| G8 | `POST /activities/{id}/join` | talent | `{ answers: [{ questionId, answer (≤500) }] }` → the same `PostJoinRequest` body as `POST /posts/{id}/joins` |
| G8 | `GET /activities/{id}/answers/{userId}` | host, or that user | `[{ questionId, question, answer }]` |
| G9 | `POST /activities/{id}/waitlist` | talent | Optional `{ answers }`. Only when the activity is full; 400 "There are still spots" otherwise |
| G9 | `DELETE /activities/{id}/waitlist` | the waiting person | |
| G9 | `GET /activities/{id}/waitlist` | host | `[{ userId, name, avatarEmoji, position, joinedAt }]` in queue order |
| G10 | `POST /activities/{id}/check-in` | someone who joined | Opens 1h before `startsAt` and closes at `endsAt` (or `startsAt` + 6h). Idempotent |
| G11 | `GET /activities/{id}/attendance` | host | `[{ joinId, userId, name, checkedInAt, outcome, outcomeRecordedAt, disputeStatus, disputeReason }]` |
| G11 | `POST /activities/{id}/attendance/dispute` | the person marked `no_show` | `{ reason (≤500) }`, within 72h of the host recording it, once |
| G11 | `PUT /activities/{id}/attendance/{joinId}/accept-dispute` | host | Marks them `attended` |
| G12 | `POST /activities/{id}/feedback` | host ↔ someone who joined | `{ toUserId, text (≤500) }`, once the activity has started. One note per pair; sending again edits it |
| G12 | `GET /activities/feedback/received` | anyone signed in | Own received feedback, newest first. Paged with `page`/`size` + `X-Total-Count`/`X-Has-More` |

`Activity` =

```
{ postId, kind, details: {…}, coverUrl, waitlistEnabled, questions: [{ id, text, required }],
  spotsLeft, waitlistCount,
  viewer: { host, joinStatus, waitlistPosition, answered, checkedInAt, outcome, disputeStatus, disputeOpenUntil } }
```

**How the pieces behave:**
- **Waitlist promotion.** When someone who had joined leaves (`DELETE /posts/{id}/joins/me`), the
  first person on the waitlist who can still join gets the spot:
  - for an open activity they're approved straight in (room, notification);
  - for an approval activity they become a pending request for the host.
  
  One spot promotes one person. People who can no longer join (blocked, not verified enough) are
  skipped. Joining directly uses up a waitlist place.
- **Attendance stays private.** The host records it with the existing
  `PUT /posts/{id}/joins/{joinId}/outcome`, and only the host and that person ever see it.
- **The 72h dispute window.**
  - Recording an outcome now starts the 72h window.
  - Marking someone present accepts their open dispute.
  - A no-show can't be re-recorded after the host accepted a dispute (400).

**⚠ Existing behaviour changes:**
- **`POST /posts/{id}/joins`** on an activity whose host added a **required question** now answers
  400: "The host asks a question before you join. Answer it to send your request." The client should
  call `POST /activities/{id}/join` with answers instead. No existing activity has questions, so
  nothing live changes, and the Jenny join tool keeps working for every activity without questions.
- **The public join-count trust signal** (on post cards) no longer drops a no-show immediately. A
  no-show counts against someone only once it is final: recorded more than 72h ago and never
  disputed. An open dispute never counts against them.

## Needs & offers — G14 to G17

`{id}` is an `ASK` (need) or `OFFER` post, created as today with `POST /posts`. A response to a
need is an **offer of help**; a response to an offer is a **request for it**. Anything else
answers 404.

| Gap | Endpoint | Who | Body / notes |
|---|---|---|---|
| G14 | `GET /needs/categories` | anyone | `["moving","repairs","tech_help","tutoring","errands","pets","rides","cooking","cleaning","gardening","career_advice","creative","other"]` |
| G14 | `GET /needs/{id}` | anyone (guest too) | `{ postId, kind: "need"\|"offer", category, preferredTime, status, responseCount, viewer: { owner, myResponse } }` |
| G14 | `PUT /needs/{id}/details` | owner | `{ category, preferredTime? (≤100) }` |
| G15 | `POST /needs/{id}/responses` | talent, not the owner | `{ message (≤500) }`. Refused on an anonymous post (accepting would open a chat that reveals the author), a closed post, a duplicate, or after the owner declined you |
| G15 | `GET /needs/{id}/responses` | signed in | The owner gets every live response, oldest first; anyone else gets only their own |
| G15 | `PUT /needs/{id}/responses/{rid}/accept` | owner | Opens the pair's private conversation (the existing one-per-pair DM, `/messages/conversations/{conversationId}`) and returns its `conversationId` |
| G15 | `PUT /needs/{id}/responses/{rid}/decline` | owner | |
| G15 | `DELETE /needs/{id}/responses/me` | the responder | Not after completion |
| G16 | `POST /needs/{id}/responses/{rid}/confirm` | owner or that responder | `{ note? (≤500) }`. It's an outcome only when **both** have confirmed (`completion.completedAt`). The first confirmation asks the other side to confirm. A need closes when completed; an offer stays open for others |
| G17 | `GET /needs/outcomes/{userId}` | anyone | That person's confirmed outcomes: `[{ postId, kind, category, role: "gave"\|"received", completedAt }]`, newest first, paged with headers. Never names the other person |
| G17 | `GET /needs/responses/mine` | signed in | "My offers" for Work: `[{ postId, postTitle, kind, response }]`, newest first, paged with headers |

`response` =

```
{ id, postId, userId, name, avatarEmoji, message, status: "pending"|"accepted"|"declined"|"withdrawn",
  createdAt, conversationId, completion: { ownerConfirmedAt, responderConfirmedAt, ownerNote, responderNote, completedAt } }
```

`conversationId` and `completion` are only in the response for the owner and that responder.

**Not built:** the Post-a-Need "Share with: nearby people only" scope. Feed, search and Discover
don't filter by it yet, and storing a privacy promise the API doesn't keep would be dishonest. The
frontend should not offer the choice until the backend enforces it.

## Career profile — G18 to G21

**What it is.** The opt-in career layer on the same identity; there is no second signup. Skills and
the CV stay where they are today (`PUT /profile/me/skills`, `POST /profile/me/cv`).

**Rules:**
- Nothing here is visible to anyone else until it's **published**.
- **Pay is private by default.**

| Gap | Endpoint | Who | Body / notes |
|---|---|---|---|
| G18 | `PUT /career/me` | talent | `{ intent, desiredRole? (≤100), experienceLevel?, workMode?, preferredLocations?[] (≤5, ≤60 chars), noticePeriod?, compensationVisibility?, expectedMin?, expectedMax? }`. `intent` is required the first time; afterwards only the fields sent change |
| G18 | `GET /career/me` | talent | Own view: every field, plus `currency: "INR"`, `openToWork`, `published`, `publishedAt` |
| G20 | `GET /career/me/preview` | talent | `{ published, compensationShownTo: "nobody"\|"employers you apply to"\|"employers", employers, connections, neighbors }`. Each audience view is built by the same code that serves other people |
| G20 | `POST /career/me/publish` | talent | `{ openToWork? }`. Refused while the intent is `explore_quietly` |
| G20 | `POST /career/me/unpublish` | talent | Also turns `openToWork` off |
| G19 | `GET /career/{userId}` | any signed-in person | The published profile as the caller's audience sees it; not published → 404 |

**Values:**

| Field | Allowed values |
|---|---|
| `intent` | `find_job`, `explore_quietly`, `offer_skills`, `hire_locally` |
| `experienceLevel` | `entry`, `junior`, `mid`, `senior`, `lead` |
| `workMode` | `any`, `onsite`, `hybrid`, `remote` |
| `noticePeriod` | `immediate`, `days_15`, `days_30`, `days_60`, `days_90` |
| `compensationVisibility` | `private` (default), `on_application`, `employers` |

`expectedMin` and `expectedMax` are yearly INR, from 0 to 1e9, with min ≤ max. Choosing
`explore_quietly` unpublishes.

**Audiences** (`CareerPublicView`: `{ userId, audience, desiredRole, experienceLevel, workMode,
preferredLocations, noticePeriod, openToWork, skills, expectedMin, expectedMax, currency }`; hidden
fields are absent):

| Audience | Who | Sees |
|---|---|---|
| `employer` | recruiter, company admin, hiring manager | Everything except pay. Pay only per the rule below |
| `connection` | people you both follow | Role, level, work mode, open to work, skills |
| `neighbor` | any other signed-in person | Role, open to work, skills |

**One pay rule, everywhere (G21).** `CompensationPolicy` decides whether an employer sees pay:
- the career range;
- **and the existing `currentCtc` / `expectedCtc`** in talent search, `GET /enterprise/talent/{id}`
  and the applicant list.

| Setting | Who sees pay |
|---|---|
| `private` (or no career profile) | nobody |
| `on_application` | employers you applied to |
| `employers` | employers you applied to, employers who unlocked you, and any employer when the profile is published |

**⚠ Existing behaviour changes (values only; no field added or removed):**
- `GET /enterprise/talent/search`, `GET /enterprise/talent/{id}` and
  `GET /enterprise/postings/{id}/applicants` return `currentCtc`/`expectedCtc` as absent unless the
  candidate shares pay. Before, any employer with full access saw them.
- The applicant list no longer returns the candidate's `homeCity`/`approxLat`/`approxLng`.
  Talent search already stripped them, because location consent covers peer discovery only; the
  applicant list was missing that.

Account erasure deletes the career profile.
