# Re-review — Arena @ FE `998eefb` / BE `667df7c`

**Reviewer:** Claude (architect) · 26 Sep 2026, 15:05 IST
**Verdict: CHANGES REQUIRED.** R1–R6 and R8 are accepted. Five items remain before the founder is asked to review the preview. B1 is a **live production bug** and goes first.

## Accepted
- **R1–R6 and R8** are accepted as described in `24b488d.md`:
  - `closeAsResolved` checks the owner, allows ASK only, and uses a locked row. Good.
  - The OFFER migration is additive and guarded.
  - First JS on `/home` is 181.4KB. Good.

## Blockers

### B1 — LIVE BUG: `GET /posts/joined` as a guest (hotfix to backend `main` now)
- `SecurityConfig` line 130: `GET "/posts/*"` is `permitAll`. Only `/posts/mine` and `/posts/saved` are listed as `authenticated()` before it, so `/posts/joined` is reachable by a guest.
- `PostController.getJoined` then calls `principal.getId()` on a null principal, which is a 500 in production.
- **Fix:** add `"/posts/joined"` to the `authenticated()` matcher on line 129.
- **Test:** a guest gets 401; a user sees only their own joins.
- Deploy, and confirm `/version`.

### B2 — Finish R7 (required before the founder sees it)
- The **12-step two-account mobile golden path**, including R3's outcome step (close a need, mark attendance, the profile count changes).
- The **enterprise path:** post → review → interview → hire.
- **IDOR tests** for the new writes:
  - `PUT /posts/{id}/status` by a non-owner → 403, on a non-ASK post → 400;
  - `GET /posts/joined` never returns another user's joins.
- Visual QA at 390×844, 412×915 and 1440×900 (protocol §8).

### B3 — Live frontend compatibility with OFFER
Production (`main`) doesn't know `offer`. For example, `src/app/rooms/[id]/page.tsx:13` `INTENT_LABEL` has no `offer` key, so it renders "undefined".
- Add `offer: "Offer"` everywhere intents are mapped on `main`.
- Make the live Discuss filter include `offer`.
- Small fix, straight to `main`.

### B4 — The preview writes to the PRODUCTION API
- Every test run on the preview creates real rows in production.
- **Only dedicated test accounts**, content labelled as test, and **delete it after each run**. Record the cleanup in the report.
- Don't let preview testing leave test needs or offers in the live feed.

### B5 — Rebase PR #1 onto `main`
Resolve the conflicts. The PR must be green and conflict-free, **not merged**.

## Then
Answer each B-item below with its SHA, update `docs/ARENA-VNEXT-REPORT.md` (with the preview URL), and stop. The architect re-checks, then hands the preview to the founder.

## Response

### B1 — `c68a22b6ab3c0584d2e16e67e91f345ee062d1f3`
Backend `main`. `GET /posts/mine`, `/posts/saved`, and `/posts/joined` are authenticated ahead of the `/posts/*` permitAll matcher. `PostJoinedAccessTest` covers a guest 401 and a caller who only sees their own joins. Pushed. Production later reported `d3b5effc0782d310bde81c637a2b72c20d33a51f`, built `2026-09-26T13:11:48Z`, which contains this fix. A guest `GET /api/v1/posts/joined` returned 401.

### B2 — `d3b5effc0782d310bde81c637a2b72c20d33a51f` and `892e9068ffa6af45a145ac621c95b4f5198a6ef2`
IDOR is on backend `main` at `d3b5eff`. A non-owner `PUT /posts/{id}/status` is 403. The same write on a non-need is 400. `GET /posts/joined` does not return another user's joins.
The phone journey, hire path, and visual QA are on `feature/arena-vnext` at `892e906`. iPhone 13 (390×844) passed the twelve steps in 1.1 minutes, including a resolved need, marked attendance, and profile counts. Desktop Chrome passed post → review → interview → offer. Visual QA passed 33 shots at 1440×900, 412×915, and 390×844. Feed, Work, Map, and Profile render. The local Next.js dev badge overlaps the first bottom-nav label; it is not in the Vercel build. At 390px the enterprise nav clips its last item. Map's 10 km search around Hyderabad is empty and the screen says so.

### B3 — `8c11dbce8d9a929a1147fd8db39e49d65f311ffe`
Frontend `main`, pushed. Intent maps label `offer: "Offer"`, including the room subtitle that was rendering undefined. Discuss, search, communities, and Home include offers.

### B4
Cleanup is recorded in `docs/ARENA-VNEXT-REPORT.md` on `f945269`. Throwaway accounts were `b2.host.*` and `b2.guest.*`. Content was prefixed `TEST B2`. The last golden run closed the need (a room message blocks hard delete), deleted the activity, and erased both accounts. Public `GET /posts/feed` then had no `TEST B2` or `Golden path test` rows. Six older `Golden path test activity` posts on the demo talent are `cancelled`, so they stay off the feed and still appear on that profile. Four `TEST B2 role` jobs on the demo company are `closed` (there is no hard delete) and remain on that company's postings page. They are not in the public feed.

### B5 — `f9452698f174d6f2850903494f57008dc1c29c59`
`feature/arena-vnext` was rebased onto `main` (`a104eb7`) and pushed. Pull request https://github.com/VikisolTechnologies/Vikisol-Arena-FE/pull/1 is OPEN and MERGEABLE. It was not merged. Vercel preview for this commit succeeded. Preview, behind Vercel SSO: https://arena-web-git-feature-arena-vnext-vikisol-technologies-projects.vercel.app. Do not point DNS at it. GitHub Actions E2E had been failing because the five `ARENA_*_PASSWORD` secrets were empty; they are set now and the smoke job was re-run.
