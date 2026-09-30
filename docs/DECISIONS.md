# Decisions

Choices made without the founder in the room. Newest first.

## 29 Sep 2026 — Architect review of `feature/be-fe-gaps`: following the app flow

The architect confirmed where the flow doc lives: `docs/design/ARENA-APP-FLOW.md` on the frontend's
`local/wip-2026-09-29`, and `docs/FE-API-GAPS.md` there has 41 rows. `docs/GAP-MAPPING.md` maps
every row. Where my first build assumed differently, **the flow doc won**:

- **Candidates may only withdraw** (architect item 2; fixes the hole logged below). Only company
  roles set other stages, and they can't set `withdrawn`. Withdrawing keeps the application as
  `withdrawn` instead of deleting it.
- **Pay goes per application** (flow §6): "Only me by default. It's shared with an employer only
  when the person applies and ticks include my CTC."
  - `CompensationPolicy` is now exactly that rule.
  - The earlier `compensationVisibility: on_application | employers` widening is retired and
    answers 400.
- **Company verification needs an Arena admin** (flow B2, §9).
  - The work-email code proves the domain; an admin then approves or rejects with a reason.
  - This replaces "nobody reviews anything by hand" below.
  - "Jobs can be drafted but not published" until verified sits behind the
    `company_verification_required` flag (off by default), so a deploy never cuts off companies
    already hiring.
- **Activities use the flow's category and subtype** with the frontend's ids (`table-tennis`,
  `all-levels`) instead of `kind`. They also gain:
  - host check-in, joiner confirm, and feedback as "Would you join again?" plus a note;
  - default reminders (24h and 2h);
  - link-only reach;
  - a trek emergency contact that is deleted a day after the trek.
- **"Women only" is a label and approval, not a filter.** Arena stores no gender (mission rule), so
  a women-only activity becomes approval-only and the host decides. It is never a search filter.
- **Need categories follow the flow** (`tech`, `pet-care`, `borrow`, …). The earlier names are still
  accepted. An offer's weekly or monthly limit is enforced when the owner accepts a request.
- **Talent search lists only published career profiles** (flow §8: "shows only people whose
  career visibility is open").
- **Employers message a talent only after an application or an accepted connect request** (flow
  §8). Chats that already exist carry on.
- **An Arena admin can reject an attendance dispute** (flow §9 disputes queue). Hosts still can
  only accept one. A dispute the admin rejected makes the no-show final at once.
- **Kept my own paths where they already did the job:**
  - two-sided completion stays at G16 (row 11);
  - the offer message stays at G15 (row 12);
  - shared evidence stays at G26 (row 32);
  - activity after-care stays under `/activities/…` (row 25).
  `GAP-MAPPING.md` names each.
- **Join with answers stays** (architect item 5). `POST /activities/{id}/join` is the way to answer
  host questions. Jenny's gateway must use it when an activity has questions; `API-CHANGES.md` says
  so for the gateway.
- **"Nearby only" on needs stays unbuilt** (architect item 3); the frontend hides it.
- **Data export and erasure cover every new table** (architect item 4, legal). This replaces the
  "not yet covered" note below.
  - `PersonalDataService` lists each table.
  - A test fails if a table added later is neither covered nor explained.
  - Recruiter and hiring-manager accounts can now be erased by an admin; company admins still
    can't, since the company would lose its admin.
- **Notification preferences stop storing a category that's off.** Safety notices can't be turned
  off.
- **People search visibility:** `everyone` is listed in any people search, `nearby` only in searches
  near a point, and `hidden` never. It changes search only, not the profile page.

**Not built, needs a decision:**
- **Billing checkout, Owner/Interviewer roles (row 35):** a paid service and an auth-model change.
- **Deleting candidate data 12 months after a role closes (flow §8):** a destructive scheduled job on
  production data.
- **Room photos and files (rows 9, 40):** wait on the messaging-architecture review.
- **Jenny covers (row 24):** JennySol's side, and the contract is still PROPOSED.

**A testing note.** Each test runs inside one outer transaction, so "all or nothing" creates (a post
with its details) are guaranteed by `@Transactional` but can't be observed rolling back in a test.
Screening answers sent with `POST /applications` are therefore validated before anything is saved,
which is also the better design.

## 29 Sep 2026 — Backend for the new frontend (`feature/be-fe-gaps`)

The brief pointed at `docs/design/ARENA-APP-FLOW.md` in the frontend repo. That file does not
exist on any frontend branch, and `docs/FE-API-GAPS.md` lists only gaps 1–6. The six areas were
built from `docs/design/BPLUS-SCREENS.md` and the frontend `ARENA-MISSION.md`, and numbered G7 and
up in `API-CHANGES.md`. Choices made along the way:

- **Everything hangs off existing rows.** Activities, needs and offers are still posts created with
  `POST /posts`; the new tables key on the post. The Jenny write bodies are unchanged, and their
  contract tests stay green.
- **Host questions vs. the Jenny join.** `POST /posts/{id}/joins` can't carry answers, so an
  activity whose host added a *required* question refuses it and points to
  `POST /activities/{id}/join`. No existing activity has questions.
- **Attendance never becomes reputation unchecked** (protocol §4). A host's no-show lowers the
  public join count only after 72h with no dispute. An open dispute never counts against the
  participant, and the host can accept it but not reject it, so an unresolved dispute stays in the
  participant's favour.
- **The private coordination room for a need is the pair's existing DM**, not a second messaging
  system (mission §5). Anonymous needs can't take responses, because accepting one would open a
  chat that reveals the author.
- **The need "Share with: nearby only" scope is not built.** Feed and search don't filter by it,
  and storing a privacy promise the API doesn't keep would be dishonest.
- **One pay rule.** `CompensationPolicy` also governs the existing `currentCtc`/`expectedCtc`, so
  "private by default" is true everywhere: a candidate without a career profile shows no pay to
  employers. The applicant list also stopped returning the approximate home location, which talent
  search already stripped.
- **Found, not changed:** `PUT /applications/{id}/stage` lets a candidate set any stage on their own
  application, `offer` and now `hired` included. The frontend may rely on it, so it is logged for
  the architect rather than changed here. Suggested fix: candidates may only withdraw.
- **Community projects are posts (`collab`), not marketplace rows.** The architecture's core primitive
  is "every Post … can open a Room", and the marketplace request body is Jenny's locked
  `createProject` contract, which requires a budget. The wire value is `collab` because `project` is
  already the feed's item type for a marketplace project.
- **Business verification proves domain control only.** The proof is a code at a work email on the
  website's domain. No documents are uploaded, and nobody reviews anything by hand. The badge says
  what it proves.
- **Not yet covered by the data export and erasure:** activity answers and feedback, need responses
  and notes, screening answers and evidence, and project join notes. Erasure already anonymises the
  person; deleting or exporting these texts is a follow-up.

## 29 Sep 2026 — Architect follow-ups on `cloud/api-hardening`

Decided by the architect; the implementation choices below are mine.

- A failed email/SMS/WhatsApp/Teams/OpenAI call answers **503** with a short message. The provider's
  text is logged server-side only, redacted. 503 rather than 502 because the user's next step is
  "try again in a minute", which is what 503 means to clients and proxies.
- Jenny's unavailable reply keeps its mandated copy ("The agent is temporarily unavailable. Your
  Arena account is still working normally.") instead of a reword. It never contained provider text.
- `/rooms` now sorts by latest activity, not join time, so the 100-row cap never hides a busy room.
  Jenny history pages are chosen newest first but read top to bottom inside a page.
- Join requests stay oldest first (a queue), and communities keep their member-count ranking.
- Paging state is in `X-Total-Count` / `X-Has-More` headers, exposed through CORS. The body stays
  an array.

## 29 Sep 2026 — Error pages are not re-authenticated; list endpoints cap at 100 rows

Branch `cloud/api-hardening`, not merged. Two security-config lines changed, logged here as the
mission asks for any auth-adjacent change.

- The servlet ERROR dispatch is `permitAll`. It only renders the status of a request that already
  failed (`ApiErrorController`, status only, no message or path). Before, a guest request that
  failed after security had run was re-checked on the error dispatch and came back as 401, hiding
  the real error. No endpoint's access rule changed.
- 403s decided by the security filter chain now write `{"success": false, "message": "Access
  denied"}` instead of an empty body. Who gets a 403 is unchanged.

List endpoints that returned every row (followers, following, blocks, rooms, DM conversations,
join requests, communities, Jenny chat history) now return the first 100 by default and accept
`page`/`size`. Their JSON stays a bare array, so neither the frontend nor the Jenny gateway's
`/communities` read changes shape. Details: `API-CHANGES.md`, `BACKEND-FIXES.md`.

## 26 Sep 2026 — A service token missing its scope returns 403

Jenny's five writes stay the same JSON. The change is only what Arena answers when a verified service token is not allowed to make the call.

Previously the agent filter recorded the denial and continued. The request then looked unauthenticated, so the entry point returned 401. The contract test now calls `POST /posts` on the public API. A token whose scope does not include `arena.createPost` gets 403, and the filter does not call the rest of the chain.

A token for a different user is also 403. The case in the test is a company-admin subject that carries `arena.createPost`. `POST /posts` is talent-only, so that token cannot publish as the other person. A second talent token that only has `arena.joinActivity` is refused the same way.

Reads are not in the scope table. A service token on those paths is still recorded and then left to the normal rules. Forcing 403 there would break Jenny's search, nearby, communities, and jobs reads, which send the same bearer.

The filter reads the path from the servlet path. When that is blank, it uses the request URI with the context path removed. Production is `/api/v1` plus `/posts`. MockMvc was leaving the servlet path empty, so the same key has to be derived both ways.

## 26 Sep 2026 — Company-admin 2FA was never required

`AuthService` asks for a code only when the role is `COMPANY_ADMIN` or `PLATFORM_ADMIN` and `totpEnabled` is already true. The column defaults to false. The demo company admin is seeded that way. The setup, enable, disable, and verify endpoints exist. Nothing in this change turns enrollment on, because that would lock every current admin out of the session the test suite uses.

Recommendation: stop at the setup step, and do not issue a normal session, until those two roles finish enrollment. Do that in its own change, with the demo accounts enrolled first.

## 30 Sep 2026 — Performance pass: indexed nearby and a 5-second feed window

Details and numbers: `docs/PERFORMANCE.md`.

- Nearby reads posts through a geohash-prefix index (`V38`) instead of the newest 500 open posts. It now finds older posts nearby, which the old read missed. It reads at most 2,000 candidates per circle, newest first.
- Feed and trending share one candidate window (posts, jobs, projects) for 5 seconds (`FEED_WINDOW_CACHE_SECONDS`, 0 turns it off). Any post, job or project write clears it after commit. Only engagement counts can lag, and only in ranking, never in what a response shows. This relaxes the earlier "no premature caching" note, because the load test showed this work was the feed's main cost.
- Search `type=all` reads activities and discussions from one window of 2,000 rows per kind. It can differ from separate reads only when one word matches more live posts than that.
- The pool stays at HikariCP's 10 (`DB_POOL_SIZE`). The JVM heap is a Railway `JAVA_OPTS` setting to make; the Dockerfile is not changed here.

## 30 Sep 2026 — Architect decisions: roles, billing, retention, verification, capacity

**Billing and roles (row 35).**
- Billing stays display-only for launch, with no payments. `GET /enterprise/admin/billing` shows the plan, seats and credits, and an empty invoice list. It used to show two made-up "paid" invoices.
- `PUT /enterprise/admin/billing/plan` refuses any change (400). It used to grant a paid plan's seats and credits for free. Arena's team sets plans until checkout exists.
- The "upgrade your plan" errors (seats, unlocks) now point to Arena's team.
- "Owner" is the existing company admin role under another name, not a new permission set. `owner` is accepted wherever a role is sent (`Role.fromWireValue`), and the role catalogue labels `company_admin` as "Owner (company admin)".
- "Interviewer" is deferred; hiring managers cover it.

**Deleting candidate data 12 months after a role closes.**
- `CandidateRetentionService` runs daily at 03:40 UTC, behind the flag `candidate_retention_enabled`, which ships OFF. A missing flag counts as off.
- **While the flag is off** each run is a dry run. It counts what it would delete and logs only the counts (applications, postings, answers, evidence, notes, events, interviews, slots), never names, emails or text.
- **The report:** `GET /admin/retention` (platform admin, 2FA) returns the same counts on demand and never deletes.
- **With the flag on**, it deletes every application to a posting closed more than 12 months ago, with its answers, evidence and assessments, notes, timeline, interviews and interview slots. The posting itself is the company's and stays.
- **The clock:** `arena_job_postings.closed_at` (V39) records when a posting was last closed. Reopening clears it. Postings closed before V39 get their last update time.
- **Switching on:** after launch, once the architect has checked the dry-run report.

**Company verification flag.**
- `company_verification_required` stays OFF now.
- **Switching it on** (off to on, through the admin flag endpoints) marks every company that isn't verified yet as verified-legacy (`verification_grandfathered_at`, V39). Legacy companies keep publishing; only companies created after the switch must verify first. The switch writes one audit entry with the count.
- **Legacy is not the public verified badge.** The company's own view shows status `verified_legacy` and `legacy: true`.
- **Admin review:** admins list legacy companies at `GET /admin/verifications/legacy`. `PUT /admin/verifications/legacy/{companyId}/end` ends one company's legacy status after review; the company is notified and must verify before its next publish, and jobs already live stay live. Each end is audited.
- Switching the flag off and on again also grandfathers companies created while it was off.

**Capacity, JVM, messaging.**
- No extra CPU or second instance for launch. The search trigram index (V40) and the scale-up trigger are in `docs/PERFORMANCE.md`.
- The `Dockerfile` defaults `JAVA_OPTS` to `-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError`. The founder sets the service to 1 GB. `railway.toml` is unchanged.
- Reading messages (GET conversations and threads) has its own limit of 60 per minute per user. Sending stays at 30.
