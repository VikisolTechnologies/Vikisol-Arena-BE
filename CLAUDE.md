# CLAUDE.md — Vikisol Arena

Read these before doing anything:
1. `docs/VIKISOL-MASTER-CONTEXT.md`: what Vikisol, Arena and JennySol are, the founder's rules, and the open decisions.
2. `docs/AGENT-COLLABORATION-PROTOCOL.md`: repository ownership, safe synchronization, independent review and release gates.
3. `docs/ARENA-MISSION.md`: the current mission and its steps.
4. The frontend repository's `docs/PROGRESS.md` and `docs/ARENA-CURRENT-STATE.md`: the shared Arena status and audit.

Older root-level `.md` plans and reports are history, not instructions. If they disagree with the four files above, the four files win.

## The short version
- **Arena is a living network, not a job board:** NEED → RESPONSE → CONVERSATION → OUTCOME → IDENTITY.
- **Target nav:** Feed, Discover, Map, Agent (Jenny, ambient), Work (outcomes), + Create, Profile.
- **Brand:** the live near-black `#09090b` + orange `#ff6b35` / `#ff8a5b`.
- **Stack:**
  - frontend: Next.js (App Router) + TypeScript + Tailwind + shadcn, on Vercel. See `AGENTS.md` for Next.js version notes.
  - backend: `Vikisol-Arena-BE`, Spring Boot 3.3 / Java 21, on Railway (`arena-staging` / `arena-api`), with Postgres + Redis.
- **Jenny:** reached only through the JennySol gateway (`~/Developer/jennysol-ai/docs/JENNY-ARENA-CONTRACT.md`). There is no AI logic in Arena, and Jenny never gets database access. Never change the shape of the Arena endpoints the gateway calls.
- **Rules:**
  - simple, cheap, free;
  - minimal code, one shared component library;
  - every visible button works;
  - mobile first;
  - honest empty states and no fake activity;
  - feature branches, commit and push often;
  - never force-push `main`;
  - never commit secrets;
  - never touch Vikisol One;
  - the new UI reaches production only after the founder approves a preview.
