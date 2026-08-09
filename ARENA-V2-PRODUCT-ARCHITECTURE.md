# ARENA V2 — Product Thesis + End-to-End Architecture
### From "job marketplace" to "post what you need, find your people"

> This is a PRODUCT DIRECTION document, not a sprint. Read it fully before writing code.
> Nothing built so far gets thrown away — jobs, projects/bidding, identity, enterprise,
> and the agent all survive. They stop being the whole product and become ONE post type
> inside a bigger primitive. Build in the phase order at the end.

---

## 1. Why it currently feels like Naukri (the honest diagnosis)
What exists today is a transactional job marketplace: postings → applications → pipeline.
That structure IS a job board, no matter how good the UI is. It has no social layer, no
real-world layer, no reason to open the app on a day you're not job hunting, and no
conversation that isn't about a vacancy.

The vision is different: **a place where people post what they need and find the people
for it — work, projects, or a game of shuttle at 4pm.** Retention doesn't come from job
search (episodic, ~once a year); it comes from the feed, the rooms, and the local
activity layer (daily). Jobs then ride on top of an already-alive network.

---

## 2. The core primitive: POST → ROOM
**One object, many intents. One outcome: people in a room together.**

Every Post has: author (person or company page), intent type, body, media, audience
(global / followers / local-radius), optional location, optional capacity, optional
deadline, visibility (public = anyone joins, private = creator approves), and tags.

Every Post can spawn a **Room** — a group chat (with optional voice/video) containing the
author and the people who joined/were approved.

| Intent type | Time horizon | Typical radius | Room becomes |
|---|---|---|---|
| `ACTIVITY` — "shuttle at 4, need 3" | hours | 1–15 km | the group that shows up |
| `HELP` / `ASK` — "need a plumber", "who's used X?" | days | local or global | Q&A thread → DM |
| `PROJECT` — paid gig, open to bids | weeks | usually global | poster → shortlisted bidders |
| `JOB` — full-time / contract | months | city or remote | recruiter → candidate |
| `UPDATE` / `THOUGHT` — feed post | — | followers/global | comment thread |
| `COMPANY` post — hiring, news, culture | — | followers/global | comment thread |

**Why this is the whole differentiator:** the same "post → people respond → room" motion
covers a badminton game and a ₹4-lakh project. Nobody else runs both on one primitive.
Naukri = posts, no rooms, no real world. LinkedIn = feed, no real world, no local density.
Meetup = activities, no work. You're the only one where your professional identity and
your Saturday game live in the same graph.

---

## 3. The surfaces (end-to-end product flow)

### 3.1 Home = Feed (the new front door — replaces "dashboard" as the default landing)
A single ranked stream mixing: people you follow, posts near you, posts matching your
identity graph (skills/interests), company pages you follow, and trending. Each card is
type-aware — an ACTIVITY card shows a mini-map + "2 of 4 spots left + Join";
a JOB card shows match % + Apply; a PROJECT card shows bids + Place bid; an UPDATE shows
like/comment/share. **Composer at the top: one button, choose intent, post.**

### 3.2 Map / Nearby (the signature screen — this is what makes it unique)
A live map of activity and local posts around you. Pins clustered by type and time.
Filters: radius, when (now / today / this week), type, public-vs-private, spots left.
Tap a pin → post preview → Join (public) or Request to join (private). This is the screen
that gets a screenshot shared on WhatsApp. Give it the 3D treatment (see §9).

### 3.3 Composer (one flow, intent-aware)
Pick intent → the form adapts: ACTIVITY asks where/when/how many/public-private; JOB asks
role/comp/location; PROJECT asks scope/budget/deadline; UPDATE is just text+media.
Location picker defaults to *approximate area*, never exact address (see §5).
The agent can draft any of them from one line ("shuttle 4pm need 3" → full post).

### 3.4 Rooms (chat + calls) — the retention engine
- **Post rooms**: auto-created group chat per post with joiners. Author is admin: approve
  requests, remove, close, mark full, cancel with notification.
- **DMs**: 1:1, spawned from a profile, an application, a bid, or a room.
- **Calls**: voice/video inside any room (see §7.4 — later phase).
- Presence, typing, read receipts, media sharing, message search. Mute/leave/report.

### 3.5 Profiles & Company Pages
- **Person**: the existing identity/talent graph, plus posts, activities joined,
  reputation/ratings from projects and activities, interests, followers/following.
- **Company Page**: separate entity a company admin manages — about, people, posts,
  open jobs, followers, culture media. Company posts appear in the feed. This is what
  gives enterprises a reason to be here between hires.

### 3.6 Trends & Discovery
Trending tags/topics globally and *near you*; "what's happening in Gachibowli today";
suggested people/companies/activities based on the identity graph and location.

### 3.7 Existing surfaces, repositioned (all retained)
Job discovery/swipe, applications pipeline, interview room, project bidding + milestones,
enterprise Talent Universe + admin consoles, the AI agent, settings — unchanged in
function, now reachable from the feed/tabs rather than being the entire app.

### 3.8 Navigation (mobile-first)
Bottom tabs: **Feed · Map · Post (+) · Rooms · Profile**. Work surfaces (Jobs, Projects,
Applications) live under a "Work" section reachable from the feed and profile. Enterprise
and admin consoles stay on their own role-gated route groups.

---

## 4. Safety & Trust — FIRST-CLASS, not a later phase
The moment strangers meet in real life from your app, safety is a core product feature
and a real-world responsibility. Non-negotiable for the activity layer:
- **Never expose exact location.** Posts carry an approximate area/landmark and a
  jittered map pin; exact meeting point is revealed only inside the room, only to
  approved joiners.
- **Verification tiers**: phone/email → work email or ID → verified badge. Creators can
  require a verification level to join. Show join-count, ratings, and account age.
- **Approval controls**: private posts require creator approval; creator can remove
  anyone; women-only / invite-only options for activity posts.
- **Report, block, and mute everywhere** (post, room, profile, message) with a real
  moderation queue behind it (the platform-admin console already has one — extend it).
- **In-room safety UX**: public-place suggestions, share-my-plan (send room details to a
  trusted contact), and a visible report control.
- **Rate limits + spam/abuse detection** on posting and joining; auto-flag phrases.
- **Under-18 handling**: activity posts and stranger-meeting features must be
  age-gated; do not let minors into stranger-meetup flows.
Treat this section as launch-blocking for the activity layer, exactly as security was for
the platform.

---

## 5. Location & privacy (DPDP-aware)
- Location is collected with explicit, separate consent; the user chooses precise, city-
  level, or off — and the app must remain usable at "off" (fall back to a chosen area).
- Store a coarse geohash/H3 cell for discovery; never render another user's precise
  point to anyone. Add per-post pin jitter.
- Background location: don't. Foreground/on-demand only.
- Retention: expire location on old posts; purge on account deletion (erasure flow
  already exists — extend it to posts, rooms, and location data).

---

## 6. Data model (additions to what exists)
- `posts`: id, author_id, author_company_id?, intent_type, body, media[], tags[],
  audience (GLOBAL|FOLLOWERS|LOCAL), visibility (PUBLIC|APPROVAL), geo (H3 cell + coarse
  point), starts_at?, ends_at?, capacity?, spots_filled, status
  (OPEN|FULL|CLOSED|CANCELLED|EXPIRED), created_at.
- `post_participants`: post_id, user_id, state (REQUESTED|APPROVED|JOINED|REMOVED|LEFT),
  decided_by, timestamps.
- `rooms`: id, post_id?, type (POST|DM|APPLICATION|PROJECT), created_at, status.
- `room_members`: room_id, user_id, role (ADMIN|MEMBER), muted, last_read_at.
- `messages`: room_id, sender_id, body, media, reply_to, created_at, deleted_at.
- `follows`: follower_id, target_user_id? / target_company_id?.
- `company_pages`: tenant_id (links to existing EnterpriseProfile), handle, about, media,
  follower_count.
- `reactions`, `comments` (on posts), `tags`, `trending_snapshots`.
- `reports`: reporter, target type/id, reason, state — feeds the moderation queue.
- `verifications`: user_id, level, method, verified_at.
- Reuse existing: users, candidate/enterprise profiles, applications, projects/bids,
  interviews, notifications, audit_events, credit ledger.

---

## 7. Technical architecture

### 7.1 Keep the current spine
Next.js (App Router) + React Native later · Spring Boot modular monolith · PostgreSQL ·
Redis · role-gated route groups · JWT + refresh · RLS/tenant scoping for enterprise. The
V2 work adds modules, not a rewrite.

### 7.2 Geospatial
- **PostGIS** (+ H3 or geohash column) on `posts` for radius queries: "open ACTIVITY posts
  within N km, starting in the next X hours, with spots left." Index on (geo, starts_at,
  status).
- Server returns coarse points only. Map tiles via a provider (Mapbox/MapLibre + OSM);
  keep the tile provider swappable and cache aggressively (cost control).

### 7.3 Feed & ranking
- **Hybrid fan-out**: fan-out-on-write to followers for normal accounts; fan-out-on-read
  for high-follower company pages (avoids write storms).
- Ranking score = recency decay × (affinity: follows/past interactions) × (proximity, for
  local posts) × (relevance: embedding match between post text/tags and the user's
  identity graph, reusing pgvector) × (urgency: activity starting soon, spots left) ×
  (quality/penalty: reports, spam signals).
- Cache computed feed pages in Redis; paginate with cursors; never full-scan.
- Trending = time-windowed tag/engagement counts per region, recomputed on a schedule.

### 7.4 Realtime (chat, presence, notifications)
- **Chat**: WebSocket (STOMP over Spring, or a dedicated realtime service) with Redis
  pub/sub for fan-out across instances. Messages persisted in Postgres; media in
  Cloudinary/S3. Delivery/read receipts, typing, presence via Redis with TTL.
- **Push/notifications**: existing notification service extended for room messages, join
  requests/approvals, activity reminders ("your game starts in 1 hour"), post responses.
- **Calls (later phase — most complex/expensive)**: don't build WebRTC from scratch. Use
  a managed SFU (LiveKit / Daily / 100ms — 100ms is India-friendly). Start with 1:1 voice,
  then group video in rooms. Budget for per-minute cost; gate behind plan if needed.

### 7.5 Moderation & anti-abuse
Auto-flag on post creation (phrase list + heuristics), report intake, moderation queue in
the platform-admin console, takedown + notify, user suspension, and rate limits on
post/join/message. Log everything to `audit_events`.

### 7.6 The AI agent, repositioned
The agent stops being "auto-apply" only and becomes the **assistant across all intents**:
draft any post from one line, summarize a busy room, suggest activities/people/jobs near
you, answer "what's happening this weekend nearby," and still do the work-side matching.
Keep the human-in-the-loop and no-fabrication rules already in place.

---

## 8. What's reused vs. new
**Reused as-is:** auth/roles/RBAC, identity & talent graph, jobs + applications +
pipeline, projects + bidding + milestones, interviews, enterprise consoles + Talent
Universe, agent, notifications, audit, security hardening.
**New modules:** posts + participants, feed + ranking, map/geo discovery, rooms/chat
(+calls later), follows, company pages, trends, reports/moderation extension,
verification tiers, location consent.
**Repositioned:** the app's front door becomes the Feed; jobs/projects become post types.

---

## 9. Design language (continue the 3D identity — don't lose it)
- Keep the dark glass + orange system, Space Grotesk/Inter, GSAP motion, and every
  existing 3D scene (standing constraint: 3D stays, tier quality, never delete).
- **Map screen is the new hero 3D moment**: subtle 3D/tilted map with glowing pins, depth,
  and clustering animation — the "Talent Universe" language applied to the real world.
- Feed stays fast and calm (3D belongs in hero/empty/loading moments, not behind a
  scrolling list). Rooms are chat-first: speed over spectacle.
- Performance rules from ARENA-PERFORMANCE.md apply to every new surface.

---

## 10. Build phases (do NOT build everything at once)
**Phase A — The primitive (this is the pivot):** posts data model + composer + feed
(basic ranking: follows + recency + proximity) + post detail + join/approve flow + rooms
with text chat + follows + notifications. Reposition navigation around Feed. Ship this and
the product stops being a job board.
**Phase B — The differentiator:** map/nearby discovery with radius+time filters, activity
post type end-to-end (capacity, spots, public/private approval, reminders), the safety
suite from §4, and location consent from §5.
**Phase C — Social depth:** company pages, comments/reactions, trends & discovery,
profile revamp (posts + activities + reputation), better feed ranking with embeddings.
**Phase D — Voice/video calls** in rooms via a managed SFU; group calls; cost controls.
**Phase E — Mobile app** (React Native/Expo) — the activity layer is inherently mobile;
this is where it truly lands.

---

## 11. Go-to-market reality (founder note, put in the report)
Hyperlocal activity products die on density, not features. **Launch the activity layer in
ONE area** — the Gachibowli/Gopanapally/HITEC City corridor — not all of India. 200 active
people in one square kilometre beats 20,000 spread across the country. Seed it with real
posts (your own network, one sport, one campus), make it work there, then expand ring by
ring. Jobs/projects can stay national from day one since they're not density-dependent.

## 12. Definition of done (per phase)
Feature complete to spec · every button works · loading/empty/error states · desktop +
mobile verified · reduced-motion safe · zero console errors · security & tenant isolation
re-verified · safety controls present for anything involving strangers meeting · perf
budgets held · docs + tagged release.
