# ARENA — MASTER ARCHITECTURE & BUILD SPECIFICATION
### Single source of truth. Supersedes every previous spec. Build all of it, in one continuous run.

> Read this file completely before writing code. Delete/ignore all earlier spec files.
> Build in the order of PART 15. No phases, no partial delivery, no stopping to ask.
> Decisions → DECISIONS.md. Missing credentials → BLOCKED.md, then continue.
> Every 3D scene stays (tiered, never deleted). Commit small and often.

---

# PART 1 — WHAT ARENA IS

**One sentence:** Arena is where you post what you need — a job, a project, a freelance
gig, or a badminton game at 4pm — and the right people near you show up.

**The core loop (every feature must serve it):**
`NEED (a post) → RESPONSE (apply / bid / join / comment) → CONVERSATION (a room) →
OUTCOME (hired, paid, met) → IDENTITY (reputation grows) → back to NEED`

**Why it isn't Naukri or LinkedIn:** a job board only matters once a year; a professional
feed has no idea you exist in a physical place. Arena has a live map of what people
around you need right now, a social feed with real media, and the work marketplace riding
on top of that daily habit. The network is the asset; access to it is the business.

**Roles**
| Role | How they get it | What they see |
|---|---|---|
| INDIVIDUAL | public signup | full consumer app |
| COMPANY_ADMIN | public company signup (creates tenant) | consumer app + company workspace + billing/team |
| RECRUITER | invited by company admin | consumer app + company workspace (no billing/team) |
| HIRING_MANAGER | invited | consumer app + assigned interviews only |
| PLATFORM_ADMIN | seeded internally | `/admin` console across all tenants |

Public signup offers **Individual** or **Company** ONLY. Never expose Recruiter /
Hiring manager / Platform admin as a self-select option anywhere.

---

# PART 2 — COMPLETE ROUTE MAP
Every route below must exist, render, and be reachable by a real link in the UI. Any
route not listed must be deleted or redirected. Produce `ROUTES.md` mirroring this table.

**Public (no auth)**
```
/                     Landing
/how-it-works         Product explainer
/for-companies        Company/enterprise pitch
/pricing              Plans
/discover             Public browse (limited; prompts sign-in on action)
/p/[postId]           Public post permalink (SEO, shareable)
/people/[handle]      Public profile
/company/[handle]     Public company page
/legal/privacy /legal/terms /legal/acceptable-use /legal/cookies
/auth                 Sign in / Sign up (tabs)
/auth/invite/[token]  Accept invitation → set password
/auth/forgot /auth/reset/[token]
*                     Branded 404 (lost orb)
```
**Authenticated — consumer**
```
/home                 The feed (default landing after sign-in)
/onboarding           First-run wizard (redirect target until complete)
/discover             Faceted search
/map                  Nearby / live map
/post/new             Composer (also a modal over any route)
/p/[postId]           Post detail (auth view: full actions)
/inbox                Conversation list (DMs + post rooms + application threads)
/inbox/[conversationId]
/work                 Work command centre (overview)
/work/applications  /work/applications/[id]
/work/bids          /work/projects  /work/projects/[id]
/work/interviews    /work/interviews/[id]   (interview room)
/work/saved
/me                   Own profile
/me/edit              Profile editor
/me/resume            CV manager
/people/[handle]      Other profiles
/notifications
/settings             (tabs: account, privacy & consent, location, notifications, security, plan)
```
**Company workspace (COMPANY_ADMIN, RECRUITER)**
```
/company/[handle]                     public page
/workspace                            company dashboard
/workspace/posts                      company posts & promotions
/workspace/posts/new                  company composer (photo/video/banner/job/project)
/workspace/jobs  /workspace/jobs/[id]
/workspace/pipeline  /workspace/pipeline/[jobId]
/workspace/talent                     candidate search (Talent Universe)
/workspace/talent/[candidateId]
/workspace/shortlists
/workspace/interviews
/workspace/inbox
/workspace/analytics                  post/job/promotion performance
/workspace/promotions                 campaigns, budgets, placements
/workspace/team                       (COMPANY_ADMIN) members, invites, seats
/workspace/billing                    (COMPANY_ADMIN) plan, credits, invoices
/workspace/audit                      (COMPANY_ADMIN) activity log
/workspace/settings                   company profile, logo, cover, about
```
**Hiring manager**: `/hm/interviews`, `/hm/interviews/[id]` only.
**Platform admin**: `/admin`, `/admin/tenants`, `/admin/tenants/[id]`, `/admin/users`,
`/admin/moderation`, `/admin/promotions`, `/admin/analytics`, `/admin/flags`.
Wrong-role access renders the branded 404 (never a redirect loop, never a blank page).

---

# PART 3 — DESIGN SYSTEM (the fix for "dull")

**Surfaces — four levels, not one.** Page `#09090B` · Card `#141416` · Hover/Active
`#1B1B1E` · Floating (modal, menu, sheet) `#232326` + shadow `0 24px 60px rgba(0,0,0,.6)`.
Borders `rgba(255,255,255,.08)` default, `.14` on emphasis. Glass (blur 18px) reserved
for nav, modals, and overlays — not for every card.

**Colour.** Brand orange `#FF6B35`→`#FF8A5B` for primary actions only. Semantic: success
`#3DDC84`, warning `#FFB020`, danger `#FF4D4F`, info `#7AA2F7`. Post-type accents (left
border + badge): JOB blue-grey, PROJECT violet, FREELANCE teal, ACTIVITY orange, ASK
amber, UPDATE neutral, COMPANY brand-of-company.

**Type.** Space Grotesk: display 40/44, H1 28/34, H2 20/26. Inter: body 16/26, small
14/20, meta 13/18. Never below 13. Colours: primary `#F5F5F6`, secondary `#A1A1AA`,
tertiary `#71717A`.

**Spacing & layout.** 8pt grid. Card padding 20–24. Gap between cards 12–16. Feed column
max-width **640px, centered**. App shell max 1440px. Never render feed content as a
multi-column card grid.

**Mandatory visual primitives** (their absence is why it looks dull):
`Avatar` (image → deterministic-colour initials), `CompanyLogo`, `MediaBlock` (1–4 photos
in a grid, or a video player with poster), `CoverImage`, `Badge`, `Chip`,
`ReactionBar`, `Skeleton`, `EmptyState` (illustration + one primary CTA), `Toast`.

**Component inventory (build once, reuse everywhere).** AppShell, TopBar, SideNav,
MobileTabBar, GlobalSearch, NotificationBell, AccountMenu, AccountSwitcher, PostCard
(variants per type), PostComposer, MediaUploader, CommentThread, ReactionBar, ShareSheet,
FollowButton, SaveButton, ReportMenu, PersonCard, CompanyCard, JobCard, ProjectCard,
ActivityCard, MapView, MapPin, MapCluster, FilterBar, FacetPanel, PipelineBoard,
ConversationList, MessageThread, MessageComposer, StatCard, DataTable, Modal, Sheet,
Drawer, Tabs, Stepper, FormField set, ConfirmDialog, Paywall/UpsellCard.

**Motion.** GSAP for page/section transitions; 150–250ms on interactions; hover lift 2px;
press scale .97; skeleton → content cross-fade. Everything respects
`prefers-reduced-motion`. No animation may delay first paint.

**3D (keep, never delete).** Orb (landing, auth, agent, loading, 404), identity graph
(profile), map depth/pin glow. All dynamically imported, mounted after first paint, quality
tiered on mobile, render loop paused when off-screen or tab hidden.

---

# PART 4 — GLOBAL SHELL

**Desktop (≥1024px):** fixed TopBar (h 64) — logo · GlobalSearch (⌘K, searches posts,
people, companies, tags) · **+ Post** (primary button) · NotificationBell (unread dot) ·
AccountMenu (avatar → profile, saved, settings, account switcher, sign out).
Left SideNav (w 240, sticky): Home · Discover · Map · Work · Inbox — divider — Saved ·
Notifications — bottom: current-account card with switcher.
Right rail (w 320) only on Home and Discover: trending tags, people to follow, nearby now,
promoted slot.
**Tablet (768–1023):** SideNav collapses to icon rail (w 72) with tooltips.
**Mobile (<768):** TopBar (logo · search icon · bell · avatar) + bottom tab bar:
**Home · Discover · ＋ · Map · Work**. The ＋ is a raised centre button opening the composer
sheet. Inbox and Profile live in the TopBar/account sheet. Safe-area insets respected; the
tab bar must never be obscured by the cookie banner or any toast.
**Account switcher:** users belonging to a company can switch between "Personal" and each
company they manage; switching changes the shell to the company workspace and changes the
composer's author. Never a separate login.

---

# PART 5 — DATA MODEL

**users** id, handle (unique), name, email, password_hash, avatar_url, cover_url,
headline, bio, location_label, geo_cell, verification_level (NONE|EMAIL|PHONE|ID),
account_age, role, status, created_at, deleted_at
**profiles** user_id, skills[], experience[], education[], open_to[], rate_floor,
availability, resume_file_id, career_score
**companies (tenants)** id, handle, name, logo_url, cover_url, about, industry, size,
website, locations[], plan, seats_total, credits_balance, follower_count, verified,
status
**memberships** user_id, company_id, role (COMPANY_ADMIN|RECRUITER|HIRING_MANAGER),
state (INVITED|ACTIVE|SUSPENDED), invited_by, joined_at
**posts** id, author_user_id?, author_company_id?, type
(JOB|PROJECT|FREELANCE|ACTIVITY|ASK|UPDATE), title, body, media[] (ordered),
tags[], skills[], location_label, geo_cell, geo_point_jittered, radius_km, starts_at,
ends_at, capacity, spots_filled, exact_meeting_point (private), compensation{min,max,
currency,period}, budget, deadline, visibility (PUBLIC|FOLLOWERS|APPROVAL),
join_policy (OPEN|APPROVAL), required_verification, status
(OPEN|FULL|CLOSED|CANCELLED|EXPIRED), promoted_campaign_id?, counts{reactions,comments,
shares,saves,responses,views}, created_at, updated_at
**media** id, owner_id, kind (IMAGE|VIDEO), url, poster_url, width, height, duration_s,
bytes, mime, alt_text, processing_status
**post_participants** post_id, user_id, state (REQUESTED|APPROVED|JOINED|REJECTED|
REMOVED|LEFT), decided_by, decided_at
**comments** id, post_id, author_id, parent_comment_id (1 level of threading), body,
media[]?, mentions[], counts{reactions,replies}, created_at, deleted_at
**reactions** target_type (POST|COMMENT), target_id, user_id, kind (LIKE default; extend
later), created_at — unique(target,user)
**shares** id, post_id, user_id, mode (REPOST|QUOTE|EXTERNAL), quote_body?, created_at
**saves** user_id, post_id, created_at
**follows** follower_user_id, target_user_id? | target_company_id?, created_at
**conversations** id, type (DM|POST_ROOM|APPLICATION|BID|COMPANY), post_id?,
application_id?, company_id?, title, status, last_message_at
**conversation_members** conversation_id, user_id, role (ADMIN|MEMBER), muted,
last_read_at
**messages** id, conversation_id, sender_id, body, media[], reply_to_id, created_at,
deleted_at
**applications** id, job_post_id, candidate_id, company_id, stage (APPLIED|SCREENING|
INTERVIEW|OFFER|HIRED|REJECTED|WITHDRAWN), tailored_cv_id, answers[], created_at
**bids** id, project_post_id, bidder_id, amount, currency, note, state (PENDING|
SHORTLISTED|WON|LOST|WITHDRAWN)
**projects_awarded** post_id, bid_id, milestones[{index,pct,amount,state,deliverable_note}],
state
**interviews** id, application_id, scheduled_at, duration, conversation_id,
meeting_url, participants[], feedback[{by,rating,strengths,concerns,decision}], state
**promotions (campaigns)** id, company_id, post_id?, creative{banner_url, video_id?,
headline, cta_label, cta_url}, placement (FEED|MAP|DISCOVER|COMPANY_SPOTLIGHT),
targeting{locations[], skills[], radius_km, roles[]}, budget_total, budget_spent,
pacing, starts_at, ends_at, state (DRAFT|PENDING_REVIEW|ACTIVE|PAUSED|COMPLETED|REJECTED),
counts{impressions,clicks,responses}
**notifications** user_id, type, actor_id, target_type, target_id, body, read_at
**reports** reporter_id, target_type (POST|COMMENT|MESSAGE|USER|COMPANY), target_id,
reason, state (OPEN|ACTIONED|DISMISSED), handled_by
**audit_events** company_id?, actor_id, action, target_type, target_id, metadata, at
**credit_ledger** company_id, actor_id, delta, reason, balance_after
**consents** user_id, purpose (LOCATION|ENTERPRISE_VISIBILITY|MARKETING|AUTO_APPLY),
granted, version, at
**verifications** user_id, level, method, verified_at

---

# PART 6 — API CONTRACT (arena-api, all under `/api/v1`)

```
AUTH      POST /auth/signup  /auth/signin  /auth/refresh  /auth/signout
          POST /auth/invite/accept   /auth/forgot   /auth/reset
FEED      GET  /feed?tab=for-you|nearby|following&cursor=       (ranked, paginated)
POSTS     POST /posts   GET /posts/{id}   PATCH /posts/{id}   DELETE /posts/{id}
          POST /posts/{id}/join           (open → JOINED, approval → REQUESTED)
          POST /posts/{id}/participants/{userId}/approve|reject|remove
          POST /posts/{id}/cancel  /posts/{id}/close
          GET  /posts/{id}/participants
COMMENTS  GET  /posts/{id}/comments?cursor=   POST /posts/{id}/comments
          POST /comments/{id}/replies   DELETE /comments/{id}
REACTIONS POST|DELETE /posts/{id}/reactions   POST|DELETE /comments/{id}/reactions
SHARE     POST /posts/{id}/share            (REPOST | QUOTE)  → creates a post
SAVE      POST|DELETE /posts/{id}/save      GET /me/saved
MEDIA     POST /media/upload-url  (signed direct upload)  GET /media/{id}
DISCOVER  GET  /search?q=&type=&skills=&location=&radius=&comp=&exp=&cursor=
MAP       GET  /map/posts?lat=&lng=&radiusKm=&window=&types=   (coarse points only)
PEOPLE    GET  /people/{handle}  GET /people/{handle}/posts
FOLLOW    POST|DELETE /follows/user/{id}   /follows/company/{id}
COMPANY   GET  /companies/{handle}  GET /companies/{handle}/posts|jobs|people
          POST /companies  PATCH /companies/{id}
WORKSPACE GET  /workspace/overview  /workspace/posts  /workspace/jobs
          GET  /workspace/pipeline/{jobId}  PATCH /applications/{id}/stage
          GET  /workspace/talent/search  POST /workspace/talent/{id}/unlock
          GET  /workspace/analytics  GET /workspace/audit
          GET|POST /workspace/team/invites  PATCH /workspace/team/{userId}
PROMO     POST /promotions  PATCH /promotions/{id}  GET /promotions
          GET  /promotions/{id}/metrics
APPLY     POST /jobs/{postId}/apply   GET /me/applications  GET /applications/{id}
BIDS      POST /projects/{postId}/bids   GET /me/bids   POST /bids/{id}/award
          POST /projects/{postId}/milestones/{i}/submit|accept
INTERVIEW POST /applications/{id}/interviews  POST /interviews/{id}/feedback
INBOX     GET  /conversations?cursor=  GET /conversations/{id}/messages?cursor=
          POST /conversations/{id}/messages   POST /conversations/dm/{userId}
          POST /conversations/{id}/mute|leave|read
NOTIFY    GET  /notifications?cursor=  POST /notifications/read
SETTINGS  GET|PATCH /me  /me/consents  /me/privacy  /me/notifications
SAFETY    POST /reports   GET|PATCH /admin/moderation
ADMIN     GET  /admin/tenants  /admin/users  /admin/analytics  PATCH /admin/tenants/{id}
REALTIME  WS   /ws  (channels: conversation:{id}, user:{id}, post:{id})
```
Rules: every list endpoint is cursor-paginated with a hard max page size; every response
returns only fields the caller's role may see; contact details never appear before an
unlock; all writes emit audit events where the model says so.

---

# PART 7 — PAGE BY PAGE
Each page below specifies: route · purpose · access · desktop layout · mobile layout ·
components · data · every interactive element and exactly what it does · states.
**Rule for all pages:** loading = skeleton, empty = illustrated EmptyState + primary CTA,
error = inline retry. No blank regions, ever. Every button does something real.

## 7.1 `/` Landing (public)
Purpose: in five seconds, a stranger understands "post what you need, people show up".
Layout (desktop): sticky glass nav (logo · How it works · Discover · For companies ·
Pricing · Sign in · **Get started**). Hero: H1 **"Post what you need."** / gradient line
**"The right people show up."**; sub: "A job, a project, or a game of badminton at 4pm —
near you or anywhere."; two CTAs (Get started, See what's nearby → `/discover`); right
side = the 3D orb (dynamically imported, poster placeholder first).
Below, in order: (1) three live example PostCards — a JOB, a PROJECT, an ACTIVITY —
rendered with the real component and real seed data; (2) an interactive map preview
section with pins; (3) "How it works" three steps (Post → People respond → You meet/hire);
(4) For companies band (hire, promote, reach) → `/for-companies`; (5) trust/safety band;
(6) footer with all legal links.
Mobile: single column, orb scaled, example cards stacked, CTAs full width.
Interactions: every nav item routes; CTAs → `/auth?mode=signup`; example cards → `/p/[id]`
public permalink; map preview → `/discover`.
**Remove entirely:** "It works while you sleep", "agent hunts openings", and the nav items
Agent / Talent Universe / Projects / Enterprise.

## 7.2 `/how-it-works`, `/for-companies`, `/pricing`, `/legal/*` (public)
How it works: the loop diagram, one section per post type with a real card example, FAQ.
For companies: hire (jobs + talent search), promote (banners, video, promoted posts),
company page preview, logos, CTA → company signup. Pricing: Individual Free/Pro,
Company Starter/Growth/Enterprise with seats, unlock credits and promotion budget;
each plan CTA routes to signup or billing. Legal pages render real published copy (if
copy is missing, render the draft with a visible "draft" note and log in BLOCKED.md).

## 7.3 `/auth` Sign in / Sign up
Split layout: left = 3D orb + one line of value copy; right = card with **Sign in |
Sign up** tabs. Sign up: account type **Individual | Company** only (two large radio
cards). Individual → name, email, password. Company → company name, work email,
password (creates tenant + COMPANY_ADMIN membership). Validation inline; password
strength meter; show/hide. Submit → JWT (access in memory/HttpOnly per PART 12) →
redirect: onboarding incomplete → `/onboarding`; else role landing (individual `/home`,
recruiter/company admin `/workspace`, hiring manager `/hm/interviews`, platform admin
`/admin`).
Also: "Forgot password" → `/auth/forgot`; invite links land on `/auth/invite/[token]` and
pre-fill email + role (read-only) and only ask for name + password.
**Delete the Recruiter / Hiring manager / Platform admin chips.**

## 7.4 `/onboarding` (first run, individual)
One question per screen with a progress bar and Back: (1) name + photo upload (skippable
but nudged — avatars matter), (2) headline + city, (3) what are you here for (multi:
work, projects, activities, network) — this seeds feed ranking, (4) skills/interests via
command-palette multi-select with fuzzy search, (5) experience + rate floor (only if
"work" chosen), (6) location consent (Precise | City only | Off, with plain-language
explanation of what each means), (7) follow suggestions — at least 5 people/companies
prefilled from seed data so the feed is never empty, (8) finish → `/home`.
Abandonment resumes at the last answered step.

## 7.5 `/home` — THE FEED (the product's front door)
Desktop: 3-column — SideNav | **feed column max 640px centered** | right rail (320).
Top of feed column: composer trigger row — Avatar + "What do you need?" pill + quick
type chips (Job · Project · Activity · Ask · Update) that open the composer preset to
that type. Then tabs: **For you · Nearby · Following**. Then the post stream, infinite
scroll, skeletons on load, "New posts" pill when fresh items arrive.
**PostCard anatomy (one component, type variants):** header row (Avatar/CompanyLogo →
name + verification tick → handle · headline · time · type Badge · overflow menu:
Save, Share, Copy link, Report, Follow/Unfollow, Delete if owner) · title (H2) · body
(clamped 4 lines + "more") · MediaBlock (1 image full-width; 2–4 in a grid; video with
poster, click-to-play, muted autoplay only when ≥50% visible and user hasn't disabled it)
· tag chips · **type block**: JOB → company, comp range, location, match %; PROJECT →
budget, deadline, N bids; FREELANCE → rate, duration; ACTIVITY → when, area, spots left,
mini static map thumb; ASK → nothing extra; UPDATE → nothing extra · **ReactionBar**:
Like (optimistic, count), Comment (opens detail or inline thread), Share (ShareSheet:
repost, quote, copy link, external), Save · **primary action button** by type: Apply /
Place bid / Join / Request to join / Answer.
Right rail: Trending tags, People to follow (FollowButton inline), Nearby now (3 activity
mini-cards → `/map`), one Promoted slot (clearly labelled "Promoted").
Mobile: single column, no rail; composer trigger sticky under TopBar; tabs scrollable.
Empty states: For you → "Follow people and companies to fill this" + suggestions;
Nearby → "Nothing nearby yet — post the first thing" + composer CTA.

## 7.6 Composer (`/post/new` + modal over any route)
Step 1: type selector (6 large cards with icon + one-line explanation).
Step 2: type-aware form, with a **live PostCard preview** beside it (desktop) or below
(mobile):
- Common: title, body (rich-ish: line breaks, links, @mentions, #tags), MediaUploader
  (up to 4 images or 1 video ≤ 90s; drag-drop, paste, progress bar, alt text field,
  reorder, remove), visibility (Everyone | Followers), tags.
- JOB: role, employment type, location + remote toggle, comp min/max/period, skills,
  experience, application questions (optional), company selector (if user manages any).
- PROJECT/FREELANCE: budget (fixed or open to bids), deadline, skills, deliverables.
- ACTIVITY: area (approximate, with "Use my location"), date+time, capacity, join policy
  (Anyone | I approve), required verification (None | Phone-verified), exact meeting
  point (private field, explicitly labelled "shown only inside the room to approved
  people").
- ASK / UPDATE: title + body + media only.
Publish → optimistic insert at top of `/home`, toast with "View post", conversation
created for ACTIVITY/ASK on first approved participant.
Validation inline; drafts autosaved to local storage; closing prompts "Discard?".

## 7.7 `/p/[postId]` Post detail
Two columns (desktop): main = full PostCard (unclamped, full media gallery with
lightbox), then **CommentThread**; right = context panel that changes by type: ACTIVITY →
map, when/where, spots, participants list, Join requests (owner: Approve/Reject/Remove);
JOB → company card, apply box, similar jobs; PROJECT → bid list with agent-pick highlight,
place-bid box; ASK/UPDATE → author card + more from author.
CommentThread: composer at top (avatar + input + media button), comments newest-first,
one level of replies, per-comment Like/Reply/Report/Delete(own or post owner), "load more",
optimistic posting, @mention autocomplete.
Owner controls: Edit (title/body/media only), Close, Cancel (with reason → notifies
participants), Delete.
Public (logged-out) view: everything readable, all actions prompt sign-in.

## 7.8 `/discover` Faceted search
Desktop: FacetPanel (240) | results (flexible) | preview (optional).
Facets: type (all 6), keyword, location + radius slider, remote toggle, compensation
range, experience, skills (multi), company, posted-within, trust level, has-media.
Sort: Relevance | Newest | Nearest | Highest paying. Saved searches (name, star, and a
"notify me" toggle). Results reuse PostCard in a compact variant; infinite scroll;
result count and active-facet chips with individual clear buttons.
Mobile: sticky filter button opening a full-height sheet; chips row shows active facets.

## 7.9 `/map` — the differentiator (must be a REAL map)
Real tiles (MapLibre GL + a dark style matched to the brand). Desktop split: map (60%)
| live result list (40%) — hovering a list item highlights its pin and vice versa;
clicking either opens the detail panel. Mobile: full-bleed map + draggable bottom sheet
(peek → half → full) listing results.
Controls: radius chips (2 / 5 / 10 / 25 km), time chips (Now · Next 3h · Today · This
week), type chips, "recenter" button, and a "Post here" FAB that opens the composer
preset to ACTIVITY with that area prefilled.
Pins: type-coloured, clustered at low zoom with counts; a pin shows spots-left; hover
preview card; selected pin opens the detail panel with Join / Request.
Privacy: server returns coarse cells + jittered points only; exact meeting point never
sent to non-approved users. Location denied → helpful state with a manual area picker
("Browse Gachibowli") rather than a dead screen.
Empty: "Nothing nearby in this window" + widen-radius shortcut + "Post the first one".

## 7.10 `/inbox` and `/inbox/[id]` — ONE messaging system
Merge Rooms and Messages. Delete `/rooms` and `/messages`; permanent-redirect both.
Desktop: list (360) | thread. Mobile: list, tap → full-screen thread with back.
List: search, filter chips (All · Groups · Direct · Work), each row = avatar/stack, title,
last message preview, time, unread dot/count, muted icon. Sorted by last_message_at.
Thread: header (title, member count/stack, context link back to the source post or
application, actions: Members, Mute, Report, Leave; owner: Manage members). Messages with
sender avatar and grouped consecutive bubbles, timestamps, read state, media messages,
reply-to quoting, link previews, day separators. Composer: text, emoji, attach media,
Enter to send, typing indicator, optimistic send with failed-state retry.
System messages: "X joined", "Y was approved", "Post cancelled", "Interview scheduled".

## 7.11 `/work` — Work command centre (not a menu of cards)
Overview: StatCards (Active applications · Interviews this week · Open bids · Live
projects · Saved), then "Needs your attention" list (interview to confirm, milestone to
accept, bid expiring), then a compact PipelineBoard preview, then recommended jobs/
projects rails.
Tabs/subroutes: **Applications** (list + filters by stage; row → `/work/applications/[id]`
showing timeline, the exact tailored CV submitted with a diff vs base CV, answers,
recruiter messages, Withdraw), **Bids** (`state` filters; row → project + your bid;
Edit/Withdraw while PENDING), **Projects** (awarded work: milestone list 30/40/30, submit
deliverable, accept & release, ratings on completion), **Interviews** (upcoming/past;
row → `/work/interviews/[id]` = interview room: join panel, participants, meeting link
embed, live notes, structured feedback for the company side), **Saved**.

## 7.12 `/me`, `/me/edit`, `/me/resume`, `/people/[handle]`
Profile hero: CoverImage (uploadable), Avatar (uploadable), name + verification tick,
headline, location, follower/following counts (clickable → lists), and actions —
own: Edit, Share, View as public; others: **Follow**, **Message**, Share, Report.
Tabs: **Overview** (about, skills with endorsement counts, experience timeline, education,
open-to chips, ratings from projects/activities), **Posts** (their PostCards), **Activity**
(joined/hosted activities, public only), **Identity graph** (the 3D talent graph as a
section, not the whole page — drag/focus/zoom, accessible list fallback), **Resume**
(own only: upload PDF/DOCX → parse → review → confirm → standard Arena CV, PDF export).
`/me/edit`: sectioned form (basics, photo/cover, about, skills, experience, education,
preferences, privacy) with autosave and a live preview link.
Public profile must render identically minus edit controls; contact details hidden unless
unlocked by a company.

## 7.13 `/company/[handle]` (public) and `/workspace/*` (company)
**Public company page:** cover + logo + name + verified tick + follower count + **Follow**
+ Message; tabs: **Posts** (all company posts incl. photos/videos/banners), **Jobs**
(open roles as JobCards), **About** (description, industry, size, website, locations),
**People** (public team members). Promoted creative may appear pinned at the top of the
company's own page.
**`/workspace` dashboard:** StatCards (active jobs, new applicants, unread messages,
followers, live promotions, credits) + "Needs attention" + recent activity + quick
actions (Post update · Post a job · Create promotion · Search talent).
**`/workspace/posts` + `/posts/new`:** the same composer, authored as the company, with
extra types: **PHOTO/VIDEO UPDATE** (media-first), **BANNER** (wide creative + headline +
CTA label + CTA URL), plus JOB and PROJECT. Grid/list of company posts with per-post
metrics (impressions, reactions, comments, shares, clicks) and a **Promote** button.
**`/workspace/jobs`:** list with status (Open/Paused/Closed), applicant counts, plan-limit
indicator; job editor; pause/close/duplicate.
**`/workspace/pipeline/[jobId]`:** Kanban (Applied → Screening → Interview → Offer →
Hired/Rejected) with drag or menu-move, candidate cards (avatar, match, applied date),
click → candidate drawer (Arena CV, answers, notes, schedule interview, message, reject
with reason). Stage changes reflect instantly on the candidate's side (same record).
**`/workspace/talent`:** search over consented candidates — query bar, facets, results as
PersonCards with match %, availability and blurred contact; **Unlock** spends a credit
(confirm dialog showing balance) and reveals contact + enables Message; direct applicants
to your own jobs are always free. Shortlists (create, add, share internally).
**`/workspace/interviews`**, **`/workspace/inbox`** (company-scoped conversations),
**`/workspace/analytics`** (posts, jobs, promotions: impressions, engagement, applicants,
conversion, follower growth; date range; CSV export).
**`/workspace/team`** (COMPANY_ADMIN): members table with role, status, last active,
per-recruiter activity (posts, unlocks, credits spent, stage moves, interviews); invite by
email with role; change role; suspend/remove with pipeline transfer; seat usage vs plan
with upsell when full.
**`/workspace/billing`**: plan, seats, credit packs, promotion budget, invoices (mock
until payments are live), upgrade/downgrade that immediately flips gates.
**`/workspace/audit`**: filterable event log (actor, action, target, time) + CSV export.
**`/workspace/settings`**: company profile, logo, cover, about, locations, handle.

## 7.14 `/notifications`
Grouped by day; types: join request, request approved/rejected, new comment, reaction,
mention, new follower, message, application status change, interview scheduled/reminder,
bid received/won/lost, milestone submitted/accepted, promotion approved/completed,
moderation outcome. Each row: actor avatar, text, time, unread dot; click → the exact
target. Mark all read; filter tabs (All · Work · Social · System). Bell shows unread count
and a dropdown of the latest 8.

## 7.15 `/settings`
Tabs: **Account** (name, handle, email, password change, connected accounts, delete
account → export + erasure flow), **Privacy & consent** (visible to companies, appear in
search, who can message me, per-purpose consent toggles with a consent history list),
**Location** (Precise | City only | Off, with a clear explanation and a "what others see"
example), **Notifications** (per-channel matrix: in-app, email, push), **Security**
(sessions, 2FA setup for admins, login history), **Plan** (current plan, upgrade, usage),
**Appearance** (reduce motion, reduce visual effects).

## 7.16 `/hm/interviews` (hiring manager) and `/admin/*` (platform)
HM: assigned interviews only (upcoming/past), interview room, structured feedback. No
pipeline, no search, no posting — enforced server-side.
Admin: tenants (list, detail, plan/credits/suspend), users (search, view, suspend,
read-only impersonate), **moderation queue** (reports + auto-flags → approve/take down,
notify owner), **promotions review** (approve/reject creatives), analytics, feature flags,
demo-data reseed. Distinct slate accent so screenshots are never confused with tenant UI.

---

# PART 8 — SOCIAL LAYER (must feel like a real network)

**Media.** Direct-to-storage upload via signed URL (never proxy large files through the
API). Images: accept JPG/PNG/WebP/HEIC ≤ 10MB, validate by magic bytes, strip EXIF,
generate 3 renditions (thumb 400, feed 800, full 1600) as WebP/AVIF, store width/height to
reserve layout space (no CLS), require alt text prompt. Video: MP4/MOV ≤ 200MB ≤ 90s,
transcode to 720p H.264 + generate poster, show duration, play muted-on-visible with tap
to unmute, never autoplay with sound, never more than one video playing at once. Lightbox
gallery with keyboard nav on click. Processing state shown on the card ("Processing…").

**Comments.** Post-level thread + one reply level. Optimistic insert, edit window of 5
minutes, delete by author or post owner, per-comment reactions, @mentions with
autocomplete that notify, #tags linked to Discover, media in comments (single image),
report per comment, "load more" pagination, counts denormalised on the post.

**Reactions.** One-tap Like with instant optimistic count and rollback on failure; unique
per user per target; long-press/hover reveals who reacted (list drawer).

**Shares.** ShareSheet with: **Repost** (appears on your profile and followers' feeds with
"X reposted"), **Quote** (adds your comment above the embedded original), **Copy link**,
and native/external share on mobile. Reposts count on the original; deleting the original
shows "This post was removed" in the repost.

**Saves, follows, mentions, tags.** Save → `/work/saved`. Follow people and companies,
with follower/following lists and mutual indicators. `@handle` and `#tag` are parsed,
linked and searchable everywhere (post body, comments, messages).

---

# PART 9 — COMPANY PROMOTION & ADVERTISING

Companies can promote themselves in four ways; all are first-class product features:
1. **Organic company posts** — photos, videos, banners, updates, jobs, projects, authored
   as the company (account switcher), appearing in followers' feeds and on the company
   page.
2. **Promoted posts** — any company post can be boosted. `/workspace/promotions` →
   Create campaign: choose post or upload creative (banner image or video + headline + CTA
   label + CTA URL), placement (**Feed** inline slot, **Map** sponsored pin, **Discover**
   top result, **Company spotlight** in the right rail), targeting (locations + radius,
   skills, roles, experience), budget total + daily pacing, schedule, preview per
   placement, submit → PENDING_REVIEW.
3. **Banners** — wide creative rendered in the feed's promoted slot, on `/discover`, and
   on the company page header area.
4. **Company spotlight** — right-rail card with logo, one line, follow button.

**Rules (non-negotiable).** Every promoted item is visibly labelled **"Promoted"** with
the company name and a "Why am I seeing this?" link explaining the targeting in plain
language. Frequency cap: at most 1 promoted item per 6 organic items in the feed;
never two consecutive. Sponsored map pins are visually distinct and never outrank a
genuine nearby activity for the same slot. Promotions go through platform-admin review
(`/admin/promotions`) before going live. Metrics recorded per impression/click/response
with de-duplication; billing draws from the company's promotion budget in the credit
ledger. Users can hide a promotion, and hiding feeds back into ranking.
**Analytics** (`/workspace/analytics`): per campaign — impressions, unique reach, clicks,
CTR, responses, spend, cost per response; per post — reach, reactions, comments, shares,
saves, profile visits, follows gained; date-range picker and CSV export.

---

# PART 10 — TRUST & SAFETY (launch-blocking for the activity layer)
Never expose exact location (coarse cell + jitter; exact meeting point only inside the
room to approved participants). Verification tiers (email → phone → ID) with a badge, and
creators may require a level to join. Approval controls: approve/reject/remove, capacity
enforcement, cancel with notification. Report and block on posts, comments, messages,
users and companies — moderation queue with takedown + notify. Auto-flag phrase scanning
on every post type. Trust signals on cards/profiles: join count, account age, "new
account" warning under 14 days, ratings. Rate limits on posting, joining, commenting,
messaging and unlocks. Age-gating so minors never enter stranger-meetup flows.
Blocked users disappear from each other's feeds, search, map and inbox.

---

# PART 11 — PERFORMANCE ARCHITECTURE (measure, don't guess)

**Rendering.** Server-render the shell and above-the-fold content on every route; stream
the rest with Suspense. Server-resolve auth so there is no client "load JS → read token →
redirect → fetch" waterfall — this is the current 10-second mobile problem. Access token
in an HttpOnly cookie so the server knows the user on the first request; do the
role-based redirect server-side.
**Bundles.** Route-level code splitting; every 3D scene and the map behind `next/dynamic
{ ssr:false }` with a poster; verify with a bundle analyser that Three.js and MapLibre
never appear in the `/home` initial chunk; narrow imports (no whole-package barrels);
target initial JS < 200KB gzipped on `/home`.
**Images/video.** `next/image` with explicit dimensions everywhere (zero CLS), AVIF/WebP,
lazy below the fold, CDN with long immutable cache; video lazy, poster-first, single
concurrent playback.
**Data.** Cursor pagination everywhere (no offset), feed page cached in Redis per user +
tab (short TTL) and invalidated on new post from followed authors; batch endpoints so a
feed render is ONE request, not N; no polling — WebSocket for new messages/notifications.
**Database.** Indexes: `posts(status, created_at)`, `posts(geo_cell, starts_at, status)`,
`posts(author_user_id)`, `posts(author_company_id)`, `comments(post_id, created_at)`,
`reactions(target_type, target_id)`, `conversation_members(user_id)`,
`messages(conversation_id, created_at)`, `applications(company_id, stage)`,
`follows(follower_user_id)`. Denormalise all counts onto posts/profiles — never
`COUNT(*)` in a feed query. Feed ranking must NOT recompute similarity over all posts per
request: precompute scores on write/cron into a materialised ranking table, then read.
**Backend runtime.** Keep the JVM warm (no scale-to-zero on the API); tune heap for the
instance; health endpoint excluded from rate limits; connection pool sized to the DB.
**Client resilience.** Request timeouts 5s (not 30), retry with backoff, circuit-breaker
UI: a clear "having trouble, retrying" banner instead of a hanging white screen.
**3D/map budget.** Mount after first paint; tier quality by device; pause render loops
when off-screen or the tab is hidden; dispose GPU resources on unmount.
**Budgets to hold:** mobile (390px, 4x CPU, Slow 4G) TTFB < 600ms, FCP < 1.8s, TTI < 3.5s,
interaction click-to-render < 300ms; 60fps desktop / ≥30fps mid-range mobile.
Report before/after numbers in `PERF-REPORT.md`.

---

# PART 12 — SECURITY
Short-lived access token + rotating refresh in HttpOnly/Secure/SameSite cookies with
server-side revocation; 2FA for company admins and platform admins; role checks enforced
server-side on every endpoint (never trust the client); tenant scoping on every
company-scoped query with automated 403-not-200 tests for cross-user and cross-tenant
object access; parameterised queries; validation on every DTO; output encoding against
stored XSS in posts, comments and messages; signed expiring media URLs; magic-byte upload
validation, size caps and malware scanning before recruiter access; security headers
(CSP, HSTS, nosniff, X-Frame-Options, Referrer-Policy); CORS locked to known origins;
rate limits on auth, upload, unlock, messaging, posting and joining; no secrets in the
client bundle (audit every `NEXT_PUBLIC_*`); no stack traces or PII in client errors or
logs; source maps not public.

---

# PART 13 — REALTIME
WebSocket channels: `user:{id}` (notifications, unread counts), `conversation:{id}`
(messages, typing, read receipts, presence), `post:{id}` (new comments, join requests,
bid arrivals, spots-left). Redis pub/sub for fan-out across instances. Reconnect with
exponential backoff and a visible connection state. Fall back to a single refetch on
reconnect — never to polling loops.

---

# PART 14 — SEED CONTENT (the app must never look dead)
Idempotent, clearly-marked demo data: 40 people with real avatar images and headlines; 10
companies with logos, covers and about copy; 60 posts spanning all types with photos and
at least 4 videos; posts spread across Gachibowli, HITEC City, Gopanapally, Madhapur,
Jubilee Hills, Kondapur; several ACTIVITY posts starting in the next 3 hours so Map and
Nearby are never empty; comments, replies and reactions on most posts; a few reposts; 3
company promotions in different placements; 8 conversations with history; a populated
pipeline for two jobs; 2 awarded projects mid-milestone.

---

# PART 15 — BUILD ORDER (one continuous run)
1. Auth/session rewrite (cookie-based, server-resolved) + shell + navigation + routing
   contract (`ROUTES.md`) — this unblocks both performance and every page.
2. Design system: tokens, surfaces, type scale, Avatar/Media/Reaction/Empty/Skeleton
   primitives, PostCard with all type variants.
3. Data model migrations + API endpoints from PART 6 (including media, comments,
   reactions, shares, follows, promotions).
4. Media pipeline (upload → renditions/transcode → render).
5. Composer (all types, both authors) → Home feed → Post detail (comments/reactions/
   shares) → Discover.
6. Map (real tiles, pins, clustering, filters, privacy rules).
7. Inbox merge (delete `/rooms` + `/messages`, redirect).
8. Work command centre (applications, bids, projects, interviews).
9. Profiles + company pages + company workspace (posts, jobs, pipeline, talent, team,
   billing, audit, analytics).
10. Promotions end-to-end + platform-admin review.
11. Notifications, settings, safety controls, HM and admin consoles.
12. Seed content.
13. Performance pass to the budgets in PART 11 + security pass in PART 12.
14. Full verification sweep, then tag `v3.0-arena-network`.

---

# PART 16 — DEFINITION OF DONE
- Every route in PART 2 exists; every interactive element on every route, as every role,
  at 1440px and 390px, does something real. `ROUTES.md` records the sweep.
- No blank regions: every list has skeleton, illustrated empty state and error+retry.
- Faces and media are visible throughout: avatars, logos, covers, photos, video.
- Comments, reactions, shares, follows, saves, mentions and tags all work end to end.
- Companies can post photos, videos and banners, promote them, and see analytics.
- The map shows real tiles with real clustered pins and respects location privacy.
- One inbox; `/rooms` and `/messages` redirect.
- Performance budgets in PART 11 met and reported; every 3D scene intact and tiered.
- Security checklist in PART 12 verified live, including cross-tenant 403 tests.
- Seed content present so no screen looks dead.
- Two-account live tests pass: post → discover on map → request → approve → conversation;
  and apply → pipeline → interview → feedback; and company post → promote → analytics.
- Zero console errors, clean lint, clean production build. Tag `v3.0-arena-network`.
