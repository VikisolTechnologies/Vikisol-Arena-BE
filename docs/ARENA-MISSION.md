# ARENA — COMPLETE MISSION FOR CURSOR (paste this whole message)

## STEP 0 — Save this mission into the repo first
Before anything else:
1. Save **this entire message, word for word**, as `docs/ARENA-MISSION.md` in the Arena frontend repo, and copy it into the backend repo's `docs/` as well. From now on, that file is your instruction set. Re-read it whenever you resume.
2. Create or keep up to date:
   - `docs/PROGRESS.md`: current step, branch, what's done, what's next, open questions. **Update it after every meaningful step**, so any agent can resume from it alone.
   - `docs/BLOCKERS.md`: things only the founder can unblock.
   - `docs/ACCESS-NEEDED.md`: one consolidated list of missing access.
   - `docs/DECISIONS.md`: every choice you made on your own, and why.
3. Commit these docs on your current branch.

**Don't restart work that's already done.** `docs/ARENA-CURRENT-STATE.md` (the audit) exists, and the cleanup is on `feature/arena-cleanup`. Continue from there.

The founder (Syam) is away. **Work continuously until STEP 7 is complete. Do not stop to ask questions.** If something is blocked:
- log it in `BLOCKERS.md`,
- take the most reasonable option,
- record it in `DECISIONS.md`,
- keep going.

---

## PART A — What Arena is (the motive)

**Arena is a living network, not a job board.** It is not Naukri, not LinkedIn, and not a resume database. Every screen serves this loop:

```
NEED → RESPONSE → CONVERSATION → OUTCOME → IDENTITY → NEW NEED
```

Target product hierarchy. The navigation must express it:

| Surface | Meaning |
|---|---|
| **Feed** | The life of Arena: what's happening now. The front door. |
| **Discover** | Who and what exists: people, companies, projects |
| **Map** | What's happening around me: needs, sessions, activities, people and opportunities nearby. Not a job map. |
| **Agent (Jenny)** | Ambient intelligence on every screen, not a chatbot tab |
| **Work** | Everything I'm involved in and its outcomes: applications, projects, bids, interviews, offers, sessions, completed outcomes |
| **+ Create** | I need something / I can offer something / start a project / create a job / create a session or activity / ask Jenny. Never "POST JOB". |
| **Profile** | My Arena identity, built from real outcomes |

**The live app today (from your audit):**
- nav is Home, Nearby, Discuss, Work, Inbox;
- the look is near-black + orange;
- jobs and projects are separate from posts;
- production commit is `3093442`.

Map this onto the hierarchy above; don't throw working features away.

**Core primitive:** every Post (ACTIVITY / HELP / PROJECT / JOB / UPDATE / COMPANY) can open a Room (conversation).

**Roles:** talent, recruiter, company_admin, hiring_manager, platform_admin.

**Sessions** are people meeting around an activity: sport, study, meetup, workshop, collaboration.
- Build on the existing ACTIVITY posts, which already have: open or approval join, an auto group Room, coarse geohash with jittered pins, meeting-point reveal, age-gating, report/block.
- Decide which fields and states are missing: date/time, capacity, RSVP states, cancellation, no-show handling, moderation.
- **Never show Session UI the backend can't honour.** Anything unsupported goes behind a flag.

**Pulse and cold start:**
- Show only **real** activity.
- Never fake numbers. "Arena is quiet right now" is correct when it's true.
- An empty network is solved with honest empty states, useful recommendations, good onboarding, nearby discovery and Jenny's help.

**Visual direction:**
- **Primary: keep the live near-black + orange palette and brand.**
- In the mockups, show an ivory/black/orange variant beside it for the founder to compare.
- Fix hierarchy, spacing, density, depth and imagery. Use editorial typography, whitespace and meaningful motion. Less uniform cards, fewer heavy borders and nested boxes, and not every module looking the same.
- Three visual languages on one brand:
  - **Living Companion**: Jenny moments. Warm, alive, a soft orb, lightweight.
  - **Talent Atlas**: Discover and Map. Network, connections, place.
  - **Studio Ledger**: Work. Clean, editorial, outcome-focused, dense.
- 3D stays but never blocks first paint: lazy-load it, quality tiers on mobile, paused when off-screen.

**Future (don't build it, don't block it):** agents may later become *Offers* inside Arena (Agent Studio/Store, around month 7–12). Keep Arena a human network now.

---

## PART B — Engineering rules (the founder's principles)

- **Simple, cheap, free, easy.** Minimal code. One shared component library, one API client, one pattern for loading/empty/error/offline states. No dead code, dead routes, duplicate systems or stale docs (archive superseded docs to `docs/archive/`).
- **Every visible button works** on mobile and desktop, for every role. Anything unfinished is hidden behind a flag.
- **Mobile first:**
  - touch targets ≥ 44px,
  - no hover-only actions,
  - bottom sheets instead of modals.
- **Performance budgets (cold mobile):**
  - landing first paint ≤ 2.5s,
  - feed interactive ≤ 3.5s,
  - first JS on `/home` ≤ 200KB gzipped. Meet this by **splitting the app shell** and lazy-loading GSAP/3D/maps, never by hiding the number.
- **WCAG AA contrast**, and zero serious or critical axe violations.
- **Never loosen a test just to make it pass.** Fix the cause.
- **Models:** use the strongest reasoning model for the blueprint, security and final review, and a cheaper model for mechanical work. A fresh agent that didn't write the code does the final review.
- **Git:**
  - feature branches only;
  - small logical commits;
  - **commit and push often** (never leave work uncommitted);
  - merge to `main` only when green;
  - never force-push `main`.

---

## PART C — JennySol inside Arena (important correction)

- **JennySol's Arena gateway is LIVE in production.** It is not a future contract.
  - Endpoints on the JennySol side: `/api/agent/gateway/chat` and `/actions/:actionId`.
  - Tools: `arena.createPost`, `arena.joinActivity`, `arena.createProject`, `arena.placeBid`, `arena.applyToJob`.
  - Flow: propose → approve → execute, with single-use actions, 5-minute expiry, and scope checks on both sides.
  - It has been verified end to end: Jenny found a real activity, proposed joining, and after approval the user was added to the room.
- **Only the contract *document* is missing.** Claude Code is writing it in the JennySol repo as `jennysol-ai/docs/JENNY-ARENA-CONTRACT.md`.
- **Before any more backend changes:**
  1. Find every Arena endpoint this gateway calls.
  2. Add contract tests for them.
  3. **Never rename, remove or change their shape.**
- **Jenny slots** go on each surface (Feed, Map, Discover, Work, + Create, Profile) and may use the live gateway. A slot renders **nothing** when Jenny has nothing real to say.
- **Jenny only drafts; the user confirms.**
- **Arena contains no AI logic** and makes no direct model calls.
- **JennySol never gets Arena database access.**

---

## PART D — Hard limits (never cross these)

1. Never touch Vikisol One / HRLMS: no code, database, or Railway project `enchanting-vibrancy`.
2. Never print, log or commit secrets. Read keys from `.env` or the Railway/Vercel CLIs only when needed.
3. No DNS changes.
4. No destructive production data operations. Migrations must be additive, backward-compatible and reversible.
5. No force-push to `main`.
6. No new paid services or keys (list them in `ACCESS-NEEDED.md` with their monthly cost).
7. No fake activity, metrics or users in production. Test content comes from dedicated test accounts, is labelled, and is deleted afterwards.
8. Never break the live JennySol gateway endpoints.
9. **The new VNext UI never reaches production without the founder's approval.** It goes to a private preview only.

---

## PART E — The steps (do them in order, without stopping)

### STEP 1 — Save and ship the cleanup
1. Commit and push `feature/arena-cleanup` **now**.
2. Re-run the full Playwright suite with `--workers=1`, or in GitHub Actions if the laptop is overloaded.
3. If it's green, merge to `main`, deploy, confirm that `/version` matches the merged commit, and smoke-test production.

This deploy contains only the fixes (contrast, guest Home, the phone header name, test fixes, deleted unused components, the company-card link fix). It contains no new screens.

### STEP 2 — Security check on enterprise sign-in
A company admin reaches the enterprise dashboard with a password only, with no 2FA. Find out whether 2FA was removed by mistake or was never enforced. Write the finding and a recommendation in `docs/ARENA-CURRENT-STATE.md` and `DECISIONS.md`. **Don't change auth behaviour without logging it.**

### STEP 3 — Protect the Jenny gateway
Do PART C: contract tests for every Arena endpoint the gateway uses. They stay green for the rest of the run.

### STEP 4 — Blueprint and mockups
Write `docs/ARENA-VNEXT-BLUEPRINT.md` (strongest model). It must explicitly answer:
1. Why would someone open Arena every day if they aren't job hunting?
2. What belongs in Feed, and how is it ranked?
3. How do Jobs, Projects, Needs, Offers, People, Sessions and Activities coexist without confusion?
4. What does Discover do that Feed doesn't?
5. What does Map do that Discover doesn't?
6. How does Jenny appear across Arena without becoming a chatbot tab?
7. What does + create, and what is the flow for each type?
8. What does Work mean beyond "my applications"?
9. What do the first 10 seconds feel like, logged out and logged in?
10. What can change with no backend work, and what needs new backend concepts?

It must also include:
- a KEEP / RECOMPOSE / REDESIGN / NEW table for every existing screen and feature;
- how today's nav (Home, Nearby, Discuss, Work, Inbox) maps onto the target;
- the final route map and navigation (mobile bottom bar + desktop);
- the data and API deltas (reuse Post → Room);
- the Sessions decision;
- Pulse and the cold-start plan;
- the Jenny slot on each surface;
- the v1 scope cut.

**Mockups:** the 6 key screens (Feed, Discover, Map, Work, + Create sheet, Profile) at phone and desktop width, saved in `docs/design/`.
- Primary option: live dark + orange. Put the ivory variant beside it.
- Wherever else there's a real choice, pick one, build it, and show the alternative beside it.

### STEP 5 — Build VNext on a private preview
Branch `feature/arena-vnext`. Build in this order:
1. the new app shell + navigation, **code-split from the start** (200KB budget)
2. Feed with Pulse
3. + Create
4. Work
5. Discover
6. Map
7. Profile
8. Jenny slots
9. the public landing page rewritten for the network positioning

**Every screen:** loading, honest empty, error and offline states. Mobile-first rules. Every button works. Anything unfinished is behind a flag.

**Backend:** only the changes the blueprint needs. They must be additive, reversible and flag-gated. They may deploy to the production API once green, because production users still see the old UI.

**Deploy the frontend to a Vercel preview URL:** protected, not indexed, not production.

### STEP 6 — Test everything on the preview
- **Playwright:** every route × every role, on desktop + Android + iPhone.
- **Mobile golden path**, with two test accounts:
  1. land
  2. sign up
  3. onboard
  4. feed
  5. create a Need
  6. the second user responds
  7. room conversation
  8. outcome recorded
  9. it shows on the profile
  10. notifications
  11. log out
  12. deep link back in
- **Enterprise path:**
  1. the company admin posts a job
  2. the recruiter reviews
  3. interview
  4. hire
- **Gates:**
  - zero console errors and zero failed requests on the golden path;
  - IDOR and role-access tests on every write;
  - axe clean;
  - performance budgets met;
  - the Jenny gateway contract tests green.
- **Independent review:** a fresh agent reviews the diff and re-runs the golden path.

### STEP 7 — Get ready for one-tap approval, then stop
Open **one** pull request, `feature/arena-vnext` → `main`, green and conflict-free. **Do not merge it.**

Write `docs/ARENA-VNEXT-REPORT.md`:
1. What's live in production now (STEP 1), with `/version`.
2. The **preview URL**, and where the test logins are (never in git).
3. What was built per surface, and which options you chose vs the alternatives.
4. Tests (numbers), performance, bundle size and lines of code before/after.
5. What's behind flags and why (especially Sessions).
6. The enterprise 2FA finding.
7. The contents of `BLOCKERS.md`, `ACCESS-NEEDED.md` and `DECISIONS.md`.
8. **The top 5 things for the founder to try on his phone** in the preview.
9. **To go live:** the exact steps (approve → merge PR → deploy → verify `/version`), plus the rollback steps.

Update `docs/PROGRESS.md`, then stop.

---

## Known founder-only items (log them, don't wait on them)
- Sentry must allow `arena.vikisol.in` (Sentry project → Allowed Domains). Until then, ignore Sentry's own 403 in the tests.
- Launch-gate items before real money or a public push: a lawyer-reviewed privacy policy/ToS, GST + Razorpay KYC, a pentest, verified backups.
