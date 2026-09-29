# ARENA — ACTIVE MISSION

**Authority:** second, after `docs/VIKISOL-MASTER-CONTEXT.md`. The full order is in `CLAUDE.md`.
**Rewritten** by the architect on 27 Sep 2026. The earlier step-by-step mission (STEPs 1–7: audit, cleanup, blueprint, VNext preview) is **complete**. It's in git history for reference and is no longer an instruction.

## 1. What Arena is
A living human network, not a job board, a LinkedIn clone or a Telegram clone:
`NEED → RESPONSE → CONVERSATION → OUTCOME → IDENTITY → NEW NEED`.

- **Surfaces:** Feed · Discover (with a List/Map mode) · (+) Create · Work · You.
- **Jenny** appears in context on those surfaces; it's never a primary tab.
- **Brand:** near-black graphite + orange.

Details are in `docs/ARENA-VNEXT-MOBILE-JENNY-BLUEPRINT.md`.

## 2. Current sequence (one mission at a time, each reviewed before the next)
1. **P0 audit fixes** (`docs/reviews/AUDIT-2026-09-26.md`), on branch **`fix/p0-security-honesty`**:
   - P0-1: demo credentials, SEED off, admin 2FA, public role chips;
   - P0-2: honest public counts, fictional seed companies, DEMO badges using `demoContent`.

   Then tests, then architect review, then merge and release on their own path.
2. **M1–M8** (`docs/ARENA-VNEXT-MOBILE-JENNY-BLUEPRINT.md` §8), on branch **`feature/arena-vnext-mobile-jenny`** from `f310ee6`. Each mission file lives in `docs/missions/`.
3. **Founder preview approval.** The approved branch then goes to production through the promotion gate (blueprint §7).
4. **Launch readiness**, after approval:
   - an indexed geohash-prefix nearby query and a distance-aware Feed; default area = launch zone;
   - launch categories enforced server-side (18+; blocked list per master context §14);
   - women-only = a host label + approval-required, **with no gender field**;
   - invite-with-a-reason links;
   - an activity waitlist;
   - a 72h attendance dispute; attendance stays private;
   - a launch-metrics page in the platform admin (SQL on existing tables).
5. **Communication layer (retention, not revenue):**
   - write `docs/ARENA-MESSAGING-ARCHITECTURE.md` first (the architect reviews it before any code);
   - then reliability: unread counts, notifications, mute, leave, block, report, correct access;
   - plus a private meeting-link field on activities.

   No native audio or video until real demand exists. Extend the existing Room/message models; never build a second messaging system.

## 3. Hard rules
- **Git:** feature branches only; never implement on `main`; never force-push; one owner per repo.
- **Secrets:** never print, log or commit secrets or credentials.
- **Out of bounds:** no DNS changes; never touch Vikisol One.
- **Data:** no destructive production data operations; migrations are additive and reversible.
- **Honesty:** no fake activity, users, counts or success; failures stay failures.
- **Jenny:**
  - there is no AI logic in Arena; everything goes through the JennySol gateway (blueprint §3–4), and Jenny never gets database access;
  - don't change the live v1 Jenny contract;
  - rely on v2 only after it's marked BUILT in `jennysol-ai/docs/JENNY-ARENA-CONTRACT.md` §6.
- **Deployment:**
  - the VNext UI reaches production only through the promotion gate;
  - never merge PR #1;
  - never create a second hosting project.
- **Quality bar:**
  - mobile first (320–430px), 44px targets, safe areas, reduced motion, WCAG AA;
  - lint, build and tests green;
  - never loosen a test to make it pass.

## 4. Founder-only items
- Gemini prepaid credit (key `…vteQ`).
- Launch-gate items (lawyer-reviewed privacy policy/ToS, GST, pentest, backups) before a public launch.
