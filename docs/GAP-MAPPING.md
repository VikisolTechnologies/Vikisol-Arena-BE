# FE-API-GAPS → backend mapping

This maps every row of the frontend's `docs/FE-API-GAPS.md` (41 rows) to the backend work that
closes it. Source: branch `local/wip-2026-09-29` of Vikisol-Arena-FE, together with
`docs/design/ARENA-APP-FLOW.md`.

- **G-numbers:** the backend gap sections in `API-CHANGES.md`.
- **"Flow" sections:** the follow-up that brought each area in line with the flow doc.
- **"Not built":** the reason is given in the row.
- Where the backend path differs from the one the FE proposed, the row says so. Where the flow doc
  differed from the first build, the flow doc won (see `docs/DECISIONS.md`).

| Row | Screen | Need | Backend | Status |
|---|---|---|---|---|
| 1 | Onboarding, why here | Store intents | Gaps 1–5: `PUT /profile/me/intents` | Built |
| 2 | Onboarding, identity | Interests | Gaps 1–5: `PUT /profile/me/interests` | Built |
| 3 | Onboarding, identity | Photo upload | Gaps 1–5: `POST/DELETE /profile/me/photo` | Built |
| 4 | Onboarding, identity | Partial profile update | Gaps 1–5: `PATCH /profile/me` | Built |
| 5 | Onboarding, identity | Availability | Gaps 1–5: `PATCH /profile/me { availability }` | Built |
| 6 | Sign in | Google client id on the preview | Frontend environment config (`NEXT_PUBLIC_GOOGLE_CLIENT_ID`); `POST /auth/google` already exists | Not built: not a backend change |
| 7 | Approved & ready | Remind me | Activities per the flow: `POST/DELETE /posts/{id}/reminder`, and default 24h/2h reminders on approval | Built (in-app notification; no push or email provider) |
| 8 | Discover | Price, for the Free chip | Activities per the flow: `priceInr` on posts and feed items | Built |
| 9 | Activity room | Photos in room messages | Needs the messaging-architecture review first (mission step 9) | Not built |
| 10 | Activity details | Host verification badge | Activities per the flow: `authorVerificationLevel` on posts and feed items | Built |
| 11 | Mark as completed | Both confirm an outcome | G16: `POST /needs/{id}/responses/{rid}/confirm` (not the FE's `/posts/{id}/outcome/confirm`) | Built at G16's path |
| 12 | Need page | Message with an offer of help | G15: `POST /needs/{id}/responses { message }` (not `/posts/{id}/joins`) | Built at G15's path |
| 13 | Offer details | Interests and recent outcomes | `interests` on `GET /profile/{id}` (gaps 1–5); outcomes with `title` on `GET /needs/outcomes/{userId}` (G17 + needs per the flow) | Built |
| 14 | Need page | Edit or pause | Posts: `PATCH /posts/{id}`, `PUT /posts/{id}/status { status }` | Built |
| 15 | Report | Evidence | People app: `POST /reports/evidence`, then `evidenceUrls` on the report endpoints | Built |
| 16 | Notifications | Category, snooze, dismiss | People app: `category`, `POST /notifications/{id}/snooze\|dismiss` | Built |
| 17 | Search | People and skills; distance | People app: `GET /search?type=people\|skills&near=&radiusKm=` | Built |
| 18 | Settings | Profile visibility; notification preferences | People app: `GET/PUT /profile/me/visibility`, `GET/PUT /notifications/preferences` | Built |
| 19 | Career setup | Career fields with per-field visibility | G18–G21 + career per the flow: `PUT /career/me` extras and `visibility` | Built |
| 20 | Apply | Screening questions, cover note, CTC sharing | G23/G24 + applications (`POST /applications { answers, coverNote, includeCtc }`) + jobs per the flow (`GET /jobs/{id}/questions`, typed questions) | Built |
| 21 | Tracker | Hired; offer accept/decline; slot pick | G25 (`hired`) + applications (`POST /applications/{id}/offer/accept\|decline`); slot pick is the existing interviews API | Built |
| 22 | Jobs | Save; must/nice; verified company | Jobs per the flow: `POST/DELETE /jobs/{id}/save`, `mustHaves`/`niceToHaves`/`companyVerified` on jobs | Built |
| 23 | Host an activity | Structured details, questions, waitlist, repeat, min size, link-only | G7–G9 + activities per the flow: `activity` and `hostQuestions` on `POST /posts`. Answers go on `POST /activities/{id}/join` (kept, architect item 5; not on `/posts/{id}/joins`) | Built |
| 24 | Cover by Jenny | AI cover | JennySol gateway; the contract is PROPOSED. Arena has uploads (`POST /activities/{id}/cover`, `POST /projects/{id}/cover`) | Not built: JennySol side |
| 25 | Activity after-care | Joiner confirm/dispute, feedback, reminders | G10–G12 + activities per the flow: `POST /activities/{id}/attendance/confirm`, `joinAgain` feedback, host check-in | Built (at `/activities/…`, not the FE's `/posts/…`) |
| 26 | Projects | Roles, applications, milestones, completion | G29–G31 + projects per the flow: `POST /projects`, `/applications`, `/milestones`, `/complete` | Built |
| 27 | Needs & offers | Radius; structured answers; offer days and limits | G14 + needs per the flow: `need` on `POST /posts`, the limit enforced on accept | Built, except the radius ("nearby only", architect item 3: agreed not built; the FE hides it) |
| 28 | Post a job | Structured fields, edit, deadline, questions, draft | Jobs per the flow: create extras, `PATCH /enterprise/postings/{id}`, `draft` | Built |
| 29 | Company workspace | Verification | G27 + verification per the flow: code, then admin approve/reject, workspace fields, logo, `/admin/verifications` | Built (publish gating behind the `company_verification_required` flag) |
| 30 | Candidate profile | Team notes; history | Applications: `GET/POST /enterprise/applicants/{id}/notes`, `GET …/events` | Built |
| 31 | Not selected / hired | Kind message; Hired | Applications: `message` on the stage change; `hired` (G25) | Built |
| 32 | Pipeline | Shared evidence; notice period | G26 evidence (`GET /enterprise/applicants/{id}/evidence`) + career per the flow: `career.noticePeriod` on applicants | Built (evidence at G26's path, not inside the applicant) |
| 33 | Interview feedback | Per must-have, no score | Jobs per the flow: `mustHaves` on `POST /interviews/{id}/feedback`, `rating` optional | Built |
| 34 | Talent | Connect requests; messaging after accept | People app: `POST /enterprise/talent/{id}/connect`, `/connect-requests`, the messaging rule | Built |
| 35 | Billing | Payment checkout; Owner/Interviewer roles | Decided 30 Sep: billing is display-only for launch (no payments, no self-service plan change); "owner" is an alias of company admin; "interviewer" deferred to hiring managers (DECISIONS.md) | Decided, display-only |
| 36 | Passwords | 8-character minimum | Gaps 1–5 work: `@Size(min = 8)` on sign-up, reset and change | Built |
| 37 | Inbox | Last message preview | People app: `lastMessagePreview` on conversations | Built |
| 38 | Feed need cards | Offer count and faces | Needs per the flow: `offerCount`, `offerAvatars` on `ask` feed items | Built |
| 39 | Need page, owner | Edit and pause | Posts: as row 14 | Built |
| 40 | Coordination room | Files tab | Needs the messaging-architecture review first (mission step 9), like row 9 | Not built |
| 41 | Work → Jobs | Save; hybrid | Jobs per the flow: saved jobs, `workMode: hybrid` | Built |

**Rows 42–54** come from the B+ branches `feature/arena-admin-bplus` (42–51) and
`feature/arena-account-bplus` (52–54). Every `/admin` path needs the platform-admin role and
2FA, and every admin action writes an audit entry, with its reason when one is given.

| # | Screen | What's needed | What closes it | Status |
|---|---|---|---|---|
| 42 | Admin — Overview | Launch metrics | `GET /admin/metrics/launch?sinceDays=`. `onboardingCompleted` stays null (nothing records it). D1/D7 return rates come from activity tracked since V41; they are null until a cohort is old enough. Demo rows and staff are left out. | Built |
| 43 | Admin — Verification | Queue, approve, reject | The existing queue, now also at `/admin/verification`. Adds `status=approved` (= verified), the fields `submittedAt` and `domainMatch`, and `{ reason }` on reject. | Built |
| 44 | Admin — Content | Browse and take down | `GET /admin/content?kind=activity\|need\|job&query=`, `PUT /admin/content/{id}/takedown { reason }`. The post is cancelled or the job closed, open reports on it are resolved, and the owner is notified. | Built |
| 45 | Admin — Content | Activity catalogue | `GET /admin/catalog/activity-types` | Built |
| 46 | Admin — Disputes | Queue with SLA; resolve | The existing queue takes `status=open\|expired\|resolved_host\|resolved_joiner`, with the fields `id, activityTitle, hostName, joinerName, openedAt, deadlineAt (opened + 72 h), state, note`. `PUT /admin/disputes/{id}/resolve { side, reason }` | Built |
| 47 | Admin — Jenny & AI | Automations, covers, providers, action log | `GET /admin/jenny/actions` (Jenny's service-token actions from the audit log) and `GET /admin/jenny/providers` (configured or not, never a key). Automations and cover flags live in JennySol, not Arena. | Partly (actions, providers) |
| 48 | Admin — Audit log | Platform-wide audit with reasons | `GET /admin/audit?sinceDays=&action=&actorId=&page=&size=`, `GET /admin/audit/export` (CSV that can't run as spreadsheet formulas) | Built |
| 49 | Admin — Team | Staff, 2FA, launch areas | `GET /admin/team`, `PUT /admin/team/{id}/launch-areas { areas }` | Built |
| 50 | Admin — Moderation | Warn, suspend, ban | `PUT /admin/moderation/{id}/warn\|suspend\|ban`, acting on the account behind the report. Dismiss and takedown now take an optional `{ reason }` and are audited. | Built |
| 51 | Admin — Users | Detail, suspend, restore, force sign-out, export/delete flags | `GET /admin/users/{id}`, `PUT …/suspend { reason, durationDays? }`, `PUT …/restore`, `POST …/force-signout`. The detail has `lastDataExportAt` and `deletionRequested`, and never any secret. | Built |
| 52 | Account — Notifications | Persist toggles | `GET/PUT /notifications/preferences` also takes and returns `messages, activities, needs, jobs`, plus `jenny` and `marketing` (opt-in) | Built |
| 53 | Account — Edit profile | PATCH with interests and photo | `PATCH /profile/me` takes `interests`. `photoUrl: ""` removes the photo; a new photo is uploaded with `POST /profile/me/photo`, never set from a URL. | Built |
| 54 | Neighbour profile | Visibility on the public profile | `GET /profile/{id}` answers 404 to anyone but the owner when: <ul><li>the profile is hidden;</li><li>it is `nearby` and the viewer is a guest;</li><li>either side blocked the other;</li><li>the account is deleted or banned.</li></ul> | Built |
| 61 | Report a person | Report a person, not a post | `POST /profile/{id}/report { reason, evidenceUrls? }`. It lands in the admin queue as a `user` report; admins warn, suspend or ban (row 50). | Built |

**Summary:**
- **Built:** 35 rows.
- **Partly built:** row 27 (all but the radius).
- **Decided, display-only:** row 35 (architect, 30 Sep: no payments at launch; "owner" = company admin; no interviewer role).
- **Not built:** rows 6, 9, 24 and 40.
  - Row 6 is frontend config.
  - Rows 9 and 40 wait on the messaging review.
  - Row 24 is JennySol's side.

**Also built from the flow doc (no FE row):**
- Admin verification queue (§9).
- Admin attendance-disputes queue (§9).
- Talent search shows only published careers (§8).
- Link-only and women-only activities (§3).
- Trek emergency contacts (§3).
- Post cancel reason (A11).
- A job takes no applications while it is a draft, paused or closed, or after its deadline (§8).
- Export and erasure over all new tables (architect item 4).

**Not built from the flow doc:**
- Deleting candidate data 12 months after a role closes (§8 Settings). This is a destructive
  scheduled job on production data and needs a founder decision.
- Owner and Interviewer team roles (§8 B3).
- Outcome disputes for needs (§9 mentions them beside attendance; needs have no dispute step yet).
