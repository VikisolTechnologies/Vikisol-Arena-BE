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
| G7 | `GET /activities/kinds` | anyone | `{ category: [subtypes] }` catalogue (flow §3 A1) with the frontend's ids (`src/lib/activities/taxonomy.ts`), e.g. `table-tennis`, `gym-buddy`: `sports, fitness, outdoors, learning, arts, games, food, community, other`. Underscores are accepted on input. `other` has no list and takes a free-text subtype |
| G7 | `GET /activities/{id}` | anyone (guest too) | `Activity` below; `viewer` only when signed in |
| G7 | `PUT /activities/{id}/details` | host | `UpdateDetails` below. `category` and `subtype` are required the first time; every other field is optional and only changes when sent |
| G13 | `POST /activities/{id}/cover` | host | multipart `file` (PNG/JPG/WebP). Needs `details` first |
| G13 | `DELETE /activities/{id}/cover` | host | |
| G8 | `PUT /activities/{id}/questions` | host | `{ questions: [{ text (≤200), required? (default true) }] }`, at most 3. Refused once anyone has answered |
| G8 | `POST /activities/{id}/join` | talent | `{ answers?: [{ questionId, answer (≤500) }], note? (≤280), emergencyContact?: { name (≤80), phone } }` → the same `PostJoinRequest` body as `POST /posts/{id}/joins`. `emergencyContact` is required for a trek |
| G8 | `GET /activities/{id}/answers/{userId}` | host, or that user | `[{ questionId, question, answer }]` |
| G9 | `POST /activities/{id}/waitlist` | talent | Optional `{ answers, note, emergencyContact }` (same as join). Only when the activity is full; 400 "There are still spots" otherwise |
| G9 | `DELETE /activities/{id}/waitlist` | the waiting person | |
| G9 | `GET /activities/{id}/waitlist` | host | `[{ userId, name, avatarEmoji, position, joinedAt }]` in queue order |
| G10 | `POST /activities/{id}/check-in` | someone who joined | Opens 1h before `startsAt` and closes at `endsAt` (or `startsAt` + 6h). Idempotent |
| G11 | `GET /activities/{id}/attendance` | host | `[{ joinId, userId, name, checkedInAt, outcome, outcomeRecordedAt, disputeStatus, disputeReason }]` |
| G11 | `POST /activities/{id}/attendance/dispute` | the person marked `no_show` | `{ reason (≤500) }`, within 72h of the host recording it, once |
| G11 | `PUT /activities/{id}/attendance/{joinId}/accept-dispute` | host | Marks them `attended` |
| G12 | `POST /activities/{id}/feedback` | host ↔ someone who joined | `{ toUserId?, joinAgain (required), note? (≤500) }`, once the activity has started. `toUserId` defaults to the host (the joiner's "Would you join again?"). One per pair; sending again edits it |
| G12 | `GET /activities/feedback/received` | anyone signed in | Own received feedback, newest first. Paged with `page`/`size` + `X-Total-Count`/`X-Has-More` |

`Activity` =

```
{ postId, category, subtype, level, cost: { type: "free"|"shared", perPersonInr, note },
  typeAnswers: { key: value }, bring: [..], accessibility, indoor, minSize, waitlist, repeat, womenOnly, reach,
  coverUrl, needsEmergencyContact, questions: [{ id, text, required }], spotsLeft, waitlistCount,
  viewer: { host, joinStatus, waitlistPosition, answered, checkedInAt, outcome, attendedConfirmed,
            disputeStatus, disputeOpenUntil, reminders: [minutesBefore] } }
```

`UpdateDetails` =

```
{ category, subtype (≤40), level: beginner|intermediate|advanced|all-levels,
  cost: { type: free|shared, perPersonInr (1–100000, required when shared), note (≤200) },
  typeAnswers: { key: text ≤300 | number | yes/no | list of ≤10 short items } (≤20 keys; the per-subtype
               questions live in the frontend's intake schema),
  bring: [≤15 items, ≤60 chars], accessibility (≤300), indoor, minSize (1–500, ≤ capacity), waitlist,
  repeat: once|weekly, womenOnly, reach: nearby|link }
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

### Activities per the app flow (FE-API-GAPS rows 7, 8, 10, 23, 25)

| FE row | Endpoint | Who | Body / notes |
|---|---|---|---|
| 23 | `POST /posts` (existing) | talent | Optional extras `activity: UpdateDetails` and `hostQuestions: [string ≤200]` (≤3). With either, the post, its details and questions are created in one transaction (all or nothing). Without them `POST /posts` behaves exactly as before |
| 23 | `PUT /posts/{id}/joins/{joinId}/approve\|decline` (existing) | host | Optional body `{ note? (≤280) }`. It is stored as `decisionNote` on the join; on a decline the joiner gets it as "A note from the host" |
| 23 | `PostJoinRequest` (response) | | Adds `note` (the joiner's note) and `decisionNote` |
| 23 | `GET /activities/{id}/emergency-contacts` | host | `[{ userId, name, contactName, contactPhone }]` for approved joiners only. Contacts are deleted a day after the activity ends |
| 25 | `PUT /activities/{id}/attendance/{joinId}/check-in` | host | Host marks someone present, from 1h before the start. Same effect as outcome `attended` (accepts an open dispute) |
| 25 | `POST /activities/{id}/attendance/confirm` | someone who joined | `{ attended, dispute? (≤500) }`, once the activity has started. Saying `attended: true` after the host marked `no_show` needs `dispute` text and opens the 72h dispute. Only the host and that person see it |
| 25 | `POST /activities/{id}/feedback` | | Now `{ toUserId?, joinAgain, note? }` (see G12) |
| 7 | `POST /posts/{id}/reminder` | host or approved joiner | `{ minutesBefore (5–10080) }` → `[minutesBefore]` the caller now has. Only for a future start. Sent as an in-app notification |
| 7 | `DELETE /posts/{id}/reminder?minutesBefore=` | same | Without `minutesBefore` it removes all of the caller's reminders for the post |
| 8 | `PostResponse.priceInr`, `FeedItemResponse.priceInr` | | `0` = free, else the shared cost per person, mirrored from `activity.cost`. `null` when not set |
| 10 | `PostResponse.authorVerificationLevel`, `FeedItemResponse.authorVerificationLevel` | | The author's verification level (`none`, `phone`, …). `null` on someone else's anonymous post |

**How the pieces behave:**
- **Default reminders.** Approved joiners get reminders 24h and 2h before the start by default. They
  can remove them. Editing the start time moves unsent reminders.
- **Women-only** (`womenOnly: true`). Arena stores no gender, so the backend can't check it.
  - The activity becomes approval-only (`visibility: "approval"`), and the host decides.
  - The label is the host's statement to joiners. It is not a filter anyone can search by.
- **Link-only** (`reach: "link"`). The post is left out of the feed, Discover, the map and other
  people's profile lists. Anyone with the link still opens it with `GET /posts/{id}`.
- **Trek emergency contact.** A joiner of a `trekking` activity must give one
  (`needsEmergencyContact: true` on the activity). Only the host sees it, after approval, and it is
  deleted a day after the trek.

**⚠ Shape change on `GET /activities/{id}` and `PUT /activities/{id}/details`.** They move from
`kind` + `details` to the flow doc's structured fields. Neither shipped in production (both are new
on this branch), and Jenny's gateway doesn't call them.

**Join with answers (Jenny's gateway).**
- The flow doc sends answers on `POST /posts/{id}/joins`. Arena keeps them on
  `POST /activities/{id}/join` (architect decision), because that endpoint body is locked for
  Jenny's gateway.
- When an activity has questions (`GET /activities/{id}` → `questions` not empty), the gateway must
  call `POST /activities/{id}/join` with `{ answers: [{ questionId, answer }] }`.
- `POST /posts/{id}/joins` answers 400 for such an activity when a question is required.
- Activities without questions still join through `POST /posts/{id}/joins`, unchanged.

### Editing, pausing and cancelling posts (FE-API-GAPS rows 14, 39; flow A10, A11)

| FE row | Endpoint | Who | Body / notes |
|---|---|---|---|
| 14, 39 | `PATCH /posts/{id}` | owner | `{ title? (≤200), body? (≤10000), startsAt?, endsAt?, locationText? (≤200), exactMeetingPoint? (≤500), tags? (≤20 × ≤40) }`. Only sent fields change; `""` clears a nullable one (never `body`). Only open, full or paused posts. A new start must be in the future and before the end. People who joined are notified of what changed; reminders follow a new start |
| 39 | `PUT /posts/{id}/status` (existing) | owner | Optional body `{ status: "paused"\|"open"\|"closed" }`. No body keeps the original behaviour (resolve a need). Pausing is for needs and offers only |
| A11 | `PUT /posts/{id}/cancel` (existing) | owner | Optional body `{ reason? (≤300) }`. The reason is in the cancellation notification to everyone who joined |
| | `PostResponse` | | Adds `cancelReason` and `editedAt` |

**What `paused` does:**
- A paused post is out of feeds, search, the map and other people's profile lists. Its owner still
  sees it.
- It takes no new joins or offers. Existing ones are kept.
- It can be reopened (`open`, or `full` when its capacity is taken) or closed.
- The stale-post expiry treats it like an open post.

**⚠ Existing enum widened:** `PostStatus` gains `paused`. Clients that switch on a post's `status`
should handle it.

## Needs & offers — G14 to G17

`{id}` is an `ASK` (need) or `OFFER` post, created as today with `POST /posts`. A response to a
need is an **offer of help**; a response to an offer is a **request for it**. Anything else
answers 404.

| Gap | Endpoint | Who | Body / notes |
|---|---|---|---|
| G14 | `GET /needs/categories` | anyone | `["moving","tutoring","repairs","tech","pet-care","plant-care","errands","borrow","rides","advice","event-help","other"]` (the flow §4 list with the frontend's ids). The earlier `tech_help`, `pets`, `gardening`, `career_advice` are still accepted on input |
| G14 | `GET /needs/{id}` | anyone (guest too) | `{ postId, kind: "need"\|"offer", category, preferredTime, status, responseCount, viewer: { owner, myResponse }, urgency, helpType, answers: {…}, offer?: { days, limit, proofUrl, limitReached } }` |
| G14 | `PUT /needs/{id}/details` | owner | `NeedDetails` below |
| G15 | `POST /needs/{id}/responses` | talent, not the owner | `{ message (≤500) }`. Refused on an anonymous post (accepting would open a chat that reveals the author), a closed post, a duplicate, or after the owner declined you |
| G15 | `GET /needs/{id}/responses` | signed in | The owner gets every live response, oldest first; anyone else gets only their own |
| G15 | `PUT /needs/{id}/responses/{rid}/accept` | owner | Opens the pair's private conversation (the existing one-per-pair DM, `/messages/conversations/{conversationId}`) and returns its `conversationId` |
| G15 | `PUT /needs/{id}/responses/{rid}/decline` | owner | |
| G15 | `DELETE /needs/{id}/responses/me` | the responder | Not after completion |
| G16 | `POST /needs/{id}/responses/{rid}/confirm` | owner or that responder | `{ note? (≤500) }`. It's an outcome only when **both** have confirmed (`completion.completedAt`). The first confirmation asks the other side to confirm. A need closes when completed; an offer stays open for others |
| G17 | `GET /needs/outcomes/{userId}` | anyone | That person's confirmed outcomes: `[{ postId, kind, category, role: "gave"\|"received", completedAt, title }]`, newest first, paged with headers. Never names the other person |
| G17 | `GET /needs/responses/mine` | signed in | "My offers" for Work: `[{ postId, postTitle, kind, response }]`, newest first, paged with headers |

`response` =

```
{ id, postId, userId, name, avatarEmoji, message, status: "pending"|"accepted"|"declined"|"withdrawn",
  createdAt, conversationId, completion: { ownerConfirmedAt, responderConfirmedAt, ownerNote, responderNote, completedAt } }
```

`conversationId` and `completion` are only in the response for the owner and that responder.

### Needs & offers per the app flow (FE-API-GAPS rows 11, 12, 13, 27, 38)

`NeedDetails` =

```
{ category (required), preferredTime? (≤100),
  urgency?: today|week|flexible, helpType?: free|exchange|costs ("costs" is for needs only),
  answers?: { key: text ≤300 | number | yes/no | list } (the category's intake answers, ≤20 keys),
  // offers only (400 on a need):
  days?: [weekdays|weekends|evenings], limit?: once-a-week|twice-a-week|a-few-times-a-month|no-limit,
  proofUrl?: http(s) link (≤500) }
```

| FE row | Endpoint | Who | Body / notes |
|---|---|---|---|
| 27 | `POST /posts` (existing) | talent | Optional extra `need: NeedDetails` on an `ask` or `offer` post. The post and its details are created in one transaction. A post can't carry both `need` and `activity` |
| 27 | `PUT /needs/{id}/responses/{rid}/accept` (existing) | owner | On an offer with a `limit`, accepting beyond it answers 400 ("You've reached the limit you set for this offer…"). The limit counts requests accepted in the last 7 days (`once-a-week` = 1, `twice-a-week` = 2) or 30 days (`a-few-times-a-month` = 3). `offer.limitReached` tells the owner ahead of time |
| 38 | `GET /feed` (existing) | anyone | Need cards (`itemType: "ask"`) carry `offerCount` (live offers of help: pending or accepted) and `offerAvatars: [{ name, avatarEmoji, photoUrl }]` (up to 3, oldest first). Absent on other item types |
| 13 | `GET /needs/outcomes/{userId}` | anyone | Adds `title` for the "recent outcomes" list. `interests` are already on `GET /profile/{id}` (gaps 1–5) |
| 11 | `POST /needs/{id}/responses/{rid}/confirm` (G16) | | The flow's "both people confirm", kept at G16's path. The FE's proposed `POST /posts/{id}/outcome/confirm` isn't added |
| 12 | `POST /needs/{id}/responses` (G15) | | The offer's message (≤500), kept at G15's path. The FE's proposed `message` on `POST /posts/{id}/joins` isn't added: a need is responded to, not joined |

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
| G20 | `GET /career/me/preview` | talent | `{ published, compensationShownTo, employers, connections, neighbors, employersYouApplyTo }`. Each audience view is built by the same code that serves other people |
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
| `compensationVisibility` | `private` only (see "Pay, per the app flow" below) |

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

**One pay rule, everywhere (G21), per the app flow (§6).** `CompensationPolicy` decides whether an
employer sees pay:
- the career range;
- the row 19 `currentCtc` / `expectedCtc`;
- **and the existing `currentCtc` / `expectedCtc`** in talent search, `GET /enterprise/talent/{id}`
  and the applicant list.

Pay is "only me" by default. It reaches an employer only when the person applies to them and ticks
**include my CTC** (`POST /applications { …, includeCtc: true }`), and only while that application
isn't withdrawn. The applicant list uses that application's own tick. Publishing, an unlock or any
setting never shares pay. `compensationVisibility` is kept for compatibility, but only `private` is
accepted: `on_application` and `employers` answer 400.

**⚠ Existing behaviour changes (values only; no field removed):**
- `GET /enterprise/talent/search`, `GET /enterprise/talent/{id}` and
  `GET /enterprise/postings/{id}/applicants` return `currentCtc`/`expectedCtc` as absent unless the
  candidate included pay on an application to that employer. Before, any employer with full access
  saw them.
- The applicant list no longer returns the candidate's `homeCity`/`approxLat`/`approxLng`.
  Talent search already stripped them, because location consent covers peer discovery only; the
  applicant list was missing that.
- **Talent search lists only people whose career profile is published** (flow §8: "shows only
  people whose career visibility is open"), on top of the existing search consent. Someone who never
  opened or published a career profile no longer appears.

### Career per the app flow (FE-API-GAPS rows 19, 32)

`PUT /career/me` takes these optional extras. Only the fields sent change; `""` or `[]` clears one.

| Field | Values |
|---|---|
| `currentCompany` | ≤80 |
| `status` | `employed`, `notice`, `between`, `student`, `freelancer` |
| `noticePeriod` | also takes the frontend's labels: `Immediate`, `15 days` … `90 days` |
| `lastWorkingDay` | `YYYY-MM-DD` |
| `experienceMonths` | 0–600 (years × 12 + months) |
| `roleFamily` | `Engineering`, `Design`, `Product`, `Data`, `SAP`, `Sales`, `Marketing`, `Operations`, `Finance`, `HR`, `Support`, `Other` |
| `skills` | `[{ name (≤40), proficiency?: learning\|working\|strong\|expert, years? (0–50) }]`, ≤20. Separate from the profile's skill tags |
| `sapModules` | the frontend's list (`FI`, `CO`, `MM`, … `S/4HANA Finance`) |
| `certifications` | ≤10 × ≤100 |
| `currentCtc` | `{ fixed, variable }`, yearly INR |
| `expectedCtc` | `{ min, max }`, yearly INR; the same values as `expectedMin`/`expectedMax` |
| `negotiable`, `relocate` | yes/no |
| `desiredRoles` | ≤3 × ≤100; the first also becomes `desiredRole` |
| `workModes` | `onsite`, `hybrid`, `remote`; one sets `workMode`, several set it to `any` |
| `shift` | `day`, `night`, `rotational`, `flexible` |
| `companySizes` | `1–10`, `11–50`, `51–200`, `201–1000`, `1000+` (a plain hyphen works too) |
| `links` | ≤5 http(s) links |
| `education` | `{ degree?: 10th\|12th\|Diploma\|Bachelor's\|Master's\|PhD\|Other, institution? (≤100), year? (1960–2035) }` |
| `languages` | ≤8 × ≤40 |
| `visibility` | `{ field: "only_me"\|"employers_i_apply"\|"public" }` for any field above, plus `noticePeriod` and `preferredLocations` |

**Visibility:**
- Defaults: `currentCompany` and `lastWorkingDay` are `employers_i_apply`, and every other field is
  `public`.
- `currentCtc` and `expectedCtc` are always `only_me`. Anything else answers 400; pay goes per
  application.
- `public` means anyone who can see the published career profile.
- `employers_i_apply` means only an employer the person has an application with.

**Responses:**
- `GET /career/me` adds `details` (every field) and `visibility` (with the defaults filled in).
- `GET /career/{userId}` and the preview add `details`: only the fields that audience may see.
- **Row 32:** `GET /enterprise/postings/{id}/applicants` items add `career`: the applicant's career
  profile as that employer sees it, published or not (applying shares it with that employer).
  - It includes `noticePeriod` and each field the person shares with employers they apply to.
  - Pay is in `career.details` only when that application includes it.
  - `career` is absent when the person has no career profile.
  - The row's `evidence` list is G26 (`GET /enterprise/applicants/{id}/evidence`), not repeated
    here.

Account erasure deletes the career profile.

## Jobs & applications — G22 to G26

**The rules:**
- Evidence is counted, never scored: "N of M must-haves", with no percentage and no ranking.
- Every employer endpoint is tenant-checked: anyone on the posting's company team can use it, and
  nobody else (403).
- Candidate endpoints only ever touch the caller's own application.

| Gap | Endpoint | Who | Body / notes |
|---|---|---|---|
| G22 | `PUT /enterprise/postings/{id}/requirements` | recruiter / company admin | `{ mustHaves: [..], niceToHaves?: [..] }`. Up to 10 each, ≤120 chars, de-duplicated, protected-attribute check. Refused once a candidate has written evidence |
| G23 | `PUT /enterprise/postings/{id}/screening` | recruiter / company admin | `{ questions: [{ text (≤200), required? }] }`. Up to 5, protected-attribute check. Refused once anyone answered |
| G22, G23 | `GET /jobs/{id}/requirements` | anyone signed in | `{ mustHaves: [{id,text}], niceToHaves: [{id,text}], screeningQuestions: [{id,text,required}] }` |
| G24 | `PUT /applications/{id}/screening` | the applicant | `{ answers?: [{ questionId, answer (≤1000) }], evidence?: [{ requirementId, evidence (≤300) }] }`. Only while `applied` or `screening`. An empty answer removes it |
| G24 | `GET /applications/{id}/screening` | the applicant | `{ answers: [{questionId, question, required, answer}], evidence: [{requirementId, kind, text, candidateEvidence}], requiredUnanswered }`. Never includes the team's assessment |
| G25 | `GET /enterprise/postings/{id}/funnel` | recruiter / company admin | `{ stages: { applied, screening, interview, offer, hired, rejected }, total }` |
| G25 | `PUT /enterprise/applicants/{id}/stage` (existing) | recruiter / company admin | Now also accepts `"hired"` |
| G26 | `GET /enterprise/postings/{id}/evidence` | recruiter / company admin | Candidate-list column: `[{ applicationId, candidateId, name, stage, summary: { mustHaves, withEvidence, met } }]`, newest application first, paged with headers |
| G26 | `GET /enterprise/applicants/{id}/evidence` | recruiter / company admin | `{ applicationId, stage, checklist: [{ requirementId, kind, text, candidateEvidence, source: "candidate"\|"not_provided", assessment, note }], answers, summary }` |
| G26 | `PUT /enterprise/applicants/{id}/requirements/{requirementId}` | recruiter / company admin | `{ assessment: "met"\|"partly"\|"not_met"\|"unclear", note? (≤500, team-private) }` |

**Notes:**
- `withEvidence` counts the must-haves the candidate wrote evidence for.
- `met` counts the must-haves the team marked `met`.
- **The Jenny apply path is unchanged.** `POST /applications` still applies with just `{ jobId }`.
  Screening answers can follow, and `requiredUnanswered` tells the candidate (and the recruiter,
  through `answers`) what's missing.
- **⚠ Existing enum widened:** `ApplicationStage` gains `hired`, so any client that switches on
  stage should handle it. Nothing sets it except a recruiter choosing it.

### Applications: stages, offers, notes and timeline (architect item 2; FE-API-GAPS rows 20, 21, 30, 31)

**Who sets which stage (architect item 2).**
- A candidate can only withdraw: `PUT /applications/{id}/stage` accepts only `"withdrawn"` from them.
  Any other stage answers 403.
- Only company roles set the other stages, through `PUT /enterprise/applicants/{id}/stage`. They
  can't set `withdrawn` (400).

| FE row | Endpoint | Who | Body / notes |
|---|---|---|---|
| 20 | `POST /applications` (existing) | talent | Optional extras `{ answers?: [{ questionId, value }], coverNote? (≤2000), includeCtc?: boolean }`. The answers are checked against the job's questions before anything is saved. `includeCtc` is the only way pay reaches an employer (see Career). `{ jobId }` alone still works (Jenny's path) |
| 21 | `DELETE /applications/{id}` (existing) | the applicant | Now keeps the application as `withdrawn` instead of deleting it, so both sides' history stays. Applying again reopens it |
| 21 | `POST /applications/{id}/offer/accept` | the applicant | Only at `offer`. Moves to `hired` |
| 21 | `POST /applications/{id}/offer/decline` | the applicant | Only at `offer`. Moves to `withdrawn` |
| 21 | `PUT /applications/{id}/outcome` | the applicant | `{ showOnProfile }`, only once `hired`. A hire shows on the profile only if chosen (flow §6) |
| 30 | `GET /applications/{id}/events` | the applicant | `[{ type: "applied"\|"stage"\|"withdrawn"\|"offer_accepted"\|"offer_declined", stage, actorName, message, at }]`, oldest first |
| 30 | `GET /enterprise/applicants/{id}/events` | the company team | The same timeline |
| 30 | `GET /enterprise/applicants/{id}/notes` | the company team | `[{ id, text, authorName, createdAt }]`, newest first. Team-private: the candidate never sees them |
| 30 | `POST /enterprise/applicants/{id}/notes` | the company team | `{ text (≤2000) }` → the notes list |
| 31 | `PUT /enterprise/applicants/{id}/stage` (existing) | the company team | Optional `message` (≤600). It goes to the candidate in the notification and the email. Moving to `rejected` ("Not selected") without a message sends Arena's kind standard one |

**⚠ Existing enum widened:** `ApplicationStage` gains `withdrawn`. Withdrawn applications don't
count as "applied" (`GET /applications/exists`).

### Jobs per the app flow (FE-API-GAPS rows 20, 22, 28, 33, 41)

| FE row | Endpoint | Who | Body / notes |
|---|---|---|---|
| 28 | `POST /enterprise/postings` (existing) | recruiter / company admin | Optional extras `{ status?: "draft"\|"open", workMode?: onsite\|hybrid\|remote, experienceLevel?: entry\|mid\|senior (the frontend's labels work too), deadline?: YYYY-MM-DD, mustHaves?, niceToHaves?, questions? }`. `workMode` sets `remote`. Pay range stays required, now with min ≤ max |
| 28 | `PATCH /enterprise/postings/{id}` | same | Any of `title, industry, location, employmentType, workMode, salaryMin, salaryMax, skills, description, experienceLevel, deadline ("" removes), mustHaves, niceToHaves, questions`. Draft or live; not closed. Must-haves and questions follow G22/G23: they can't change once a candidate answered them |
| 28 | `PUT /enterprise/postings/{id}/status` (existing) | same | Also takes `"draft"`, but only while nobody has applied. Publishing a draft (or reopening a closed posting) counts against the plan's posting limit |
| 28 | `GET /enterprise/postings`, `GET /enterprise/postings/{id}` | same | Add `workMode`, `experienceLevel`, `deadline` |
| 20, 28 | `PUT /enterprise/postings/{id}/screening` (G23) | same | Each question also takes `type: text\|yesno\|number\|choice` (default `text`) and, for `choice`, `options` (2–8, ≤80 each) |
| 20 | `GET /jobs/{id}/questions` | signed in | `[{ id, type, label, options, required }]` (`options` only on `choice`) |
| 20 | `PUT /applications/{id}/screening` (G24), `POST /applications` | the applicant | Answers must fit the type: `yes`/`no` (also `true`/`false`, stored as `yes`/`no`), a number, or one of the options (case-insensitive; stored as written in the options) |
| 22, 41 | `POST /jobs/{id}/save`, `DELETE /jobs/{id}/save` | signed in | Private bookmark. Saving twice is fine. A draft can't be saved (404) |
| 22, 41 | `GET /jobs/saved` | signed in | `[JobResponse]`, newest saved first, paged with headers |
| 22, 28, 41 | `GET /jobs`, `GET /jobs/{id}`, `GET /companies/{id}/jobs` | | Jobs add `workMode`, `experienceLevel`, `deadline`, `mustHaves: [text]`, `niceToHaves: [text]`, `companyVerified` (G27) and `saved` (absent for a guest) |
| 33 | `POST /interviews/{id}/feedback` (existing) | recruiter / company admin / assigned hiring manager | Adds `mustHaves: [{ item (≤120), seen: strong\|some\|none, note? (≤500) }]` (≤10). `rating` is now optional (1–5 when sent) |

**How the pieces behave:**
- **Drafts.** A draft is only for its company team.
  - `GET /jobs/{id}`, `/requirements` and `/questions` answer 404 to anyone else.
  - It never appears in `/jobs` or on the company page, and can't be applied to.
- **Applying.** Only an `open` job takes applications: a paused or closed one answers 400. After the
  deadline (India time) it also answers 400.
- **Live postings.** The plan's posting limit and the company page's `openJobCount` count open and
  paused postings; drafts and closed ones don't.

**⚠ Existing behaviour changes:**
- `POST /applications` to a paused or closed job used to succeed; it now answers 400.
- `GET /companies/{id}/jobs` never shows drafts.
- `InterviewResponse.feedback.rating` is absent when the interviewer gave none (it was `0`).
- **⚠ Existing enum widened:** `PostingStatus` gains `draft`.

## Business verification and team roles — G27, G28

| Gap | Endpoint | Who | Body / notes |
|---|---|---|---|
| G27 | `POST /enterprise/verification` | company admin | `{ legalName (≤200), website, workEmail, submitterRole }`. `submitterRole` is one of `founder`, `hr`, `talent_acquisition`, `hiring_manager`, `operations`, `other`: the "Your role" dropdown, the submitter's job at the company, not a permission. Sends a 6-digit code to the work email |
| G27 | `POST /enterprise/verification/confirm` | company admin | `{ code }`. The right code turns the status to `verified` |
| G27 | `GET /enterprise/verification` | anyone on the company team | `{ status: "not_started"\|"pending"\|"verified", legalName, website, domain, workEmail (masked, "a***@domain"), submitterRole, codeExpiresAt, verifiedAt }` |
| G27 | `GET /companies/{id}/verification` | anyone (guest too) | `{ verified, domain, verifiedAt }` for the badge. `verified: false` until a code is confirmed |
| G28 | `GET /enterprise/team/roles` | anyone on a company team | `[{ role: "company_admin"\|"recruiter"\|"hiring_manager", label, can: [...] }]`. Each capability mirrors a real API guard, so the Team screen can't promise something the API refuses. Inviting, changing roles, suspending and removing members were already there (`/enterprise/admin/team/*`) and are unchanged |

**What the badge proves: control of the website's domain, nothing more.**
- The work email must be at the website's domain or one of its subdomains, and not a personal
  mailbox (gmail, outlook, yahoo, …); otherwise 400.
- The code:
  - lasts 30 minutes;
  - allows 5 wrong tries;
  - can be re-sent once a minute;
  - is stored only as a hash and cleared once used.
- Submitting again (for example with new details) starts over and removes the badge until the new
  code is confirmed.
- If the email can't be sent, the answer is the usual 503 with "We couldn't send the code right
  now…", and nothing is verified.

## Community projects and profile stats — G29 to G32

**What a community project is:**
- A `collab` post ("Start a project"), created with the existing `POST /posts`:
  `{ "intentType": "collab", "title", "body", "visibility": "public"|"approval", … }`.
- People join it for an open role. Its Room is the team space.
- It is separate from the paid marketplace (`/marketplace/projects`: bids, award, milestones),
  which is unchanged, and from the Jenny `createProject` tool, which still creates marketplace
  projects.
- The wire value is `"collab"` so it never collides with the feed's `"project"` item type
  (a marketplace project).
- A collab post can't be anonymous.

| Gap | Endpoint | Who | Body / notes |
|---|---|---|---|
| G29 | `POST /posts` (existing) | talent | Now accepts `"intentType": "collab"` |
| G29 | `PUT /projects/{id}/roles` | owner | `{ roles: [{ title (≤80), description? (≤300), slots? (1–50, default 1) }] }`, up to 10, protected-attribute check. Refused once anyone asked for a role |
| G29 | `POST /projects/{id}/join` | talent | `{ roleId?, message? (≤500) }` → the usual `PostJoinRequest`. A role is required when the project has roles; a filled role is refused |
| G30 | `GET /projects/{id}` | anyone (guest too) | `{ postId, title, status, roles: [{id,title,description,slots,filled}], team: [{userId,name,avatarEmoji,roleId,roleTitle}], viewer: { owner, joinStatus, roleId } }`. `team` lists only people who are in |
| G30 | `GET /projects/{id}/requests` | owner | `[{ joinId, userId, name, status, roleId, roleTitle, message }]`. Approve or decline with the existing `PUT /posts/{id}/joins/{joinId}/approve\|decline` |
| G31 | `GET /projects/of/{userId}` | anyone | Projects that person started or is in: `[{ postId, title, status, role: "owner"\|role title\|"member", createdAt }]`, newest first, paged with headers |
| G32 | `GET /profile/{userId}/stats` | anyone | `{ hosted, joined, helped, projects }`: the profile stat row |

**How the G32 stats are counted** (real counts only):

| Stat | Counts |
|---|---|
| `hosted` | activities the person hosted in their own name that weren't cancelled or removed |
| `joined` | activities they were approved into. A no-show drops out only once final and undisputed (G11) |
| `helped` | confirmed outcomes where they gave help (G16/G17) |
| `projects` | the same set as G31 |

**Known limit:** role capacity is checked when someone asks, not when the owner approves. Approving
more people than a role's slots is possible and shows as `filled > slots`. The owner sees it; it
never blocks anyone.

**⚠ Existing enum widened:** `PostIntentType` gains `collab`.
- It also appears as a feed `itemType` and a post `intentType`, so clients that switch on either
  should handle it.
- A `collab` post is joinable (`joinable: true`) like activities and needs.
