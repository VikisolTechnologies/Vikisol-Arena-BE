# CLAUDE.md — Vikisol Arena

## Which documents are instructions (authority order)
1. `docs/VIKISOL-MASTER-CONTEXT.md`: the ecosystem, the founder's rules, decisions.
2. `docs/ARENA-MISSION.md`: the active mission and sequence.
3. `docs/ARENA-VNEXT-MOBILE-JENNY-BLUEPRINT.md`: the VNext design, navigation, Jenny contract status, branches and deployment.
4. `docs/PROGRESS.md`: where the last agent stopped. Resume from it; don't restart.
5. `docs/missions/*.md`: one bounded mission at a time.

Also follow `docs/AGENT-COLLABORATION-PROTOCOL.md` (how agents work together) and `docs/reviews/` (the architect's verdicts; the newest one governs its SHA).

**Everything else is historical evidence, not instructions.** That includes root-level `*.md` plans and reports (ARENA-MASTER-ARCHITECTURE, PRODUCT_BIBLE, ROUTES, COMPLETION-REPORT and so on) and older docs. Where they disagree with the files above, the files above win.

## The short version
- **Arena** is a living network: NEED → RESPONSE → CONVERSATION → OUTCOME → IDENTITY.
- **Mobile bottom bar:** Feed · Discover · (+) · Work · You. Map is a List/Map mode inside Discover. Jenny is contextual, never a primary tab. Inbox is in the signed-in header.
- **Brand:** near-black graphite + orange.
- **Stack:** frontend on Next.js + TypeScript + Tailwind + shadcn (Vercel; see `AGENTS.md`); backend `Vikisol-Arena-BE` on Spring Boot 3.3 / Java 21 (Railway `arena-api`).
- **Jenny:** only through the JennySol gateway; the v1 contract is live and must not break. The demo flag is **`demoContent`**.
- **Rules:**
  - feature branches only;
  - never commit secrets;
  - never touch Vikisol One;
  - no fake data;
  - VNext goes only to the protected preview until the founder approves.
