# VIKISOL ECOSYSTEM — MAC HANDOFF (Arena-side index)

**READ THIS BEFORE MODIFYING THIS REPOSITORY.**

The full ecosystem handoff document lives in the `Jennysol-AI` repository:
`VIKISOL-ECOSYSTEM-MAC-HANDOFF.md` (repo root). This file is a short pointer for anyone who lands
in `Vikisol-Arena-BE` first — read the full document before making changes here.

## Arena-specific quick facts (2026-09-11 checkpoint)

- **HEAD at this checkpoint**: `95bf156` (`fix(security): pin AgentServiceTokenVerifier to HS256
  only (M10)`). Check `git log`/`git fetch` for anything newer before trusting this.
- **The real production service is `arena-api` in the Railway project named `arena-staging`**,
  serving `api-arena.vikisol.in`. A separate, failing, orphaned Railway project literally named
  `Vikisol-Arena` (service `Vikisol-Arena-BE`) shares this same GitHub repo, auto-deploys
  independently, and serves no real traffic — do not confuse the two. Always run `railway status`
  after linking and check the `url` field.
- **This backend's own agent-integration code** (`agent/`, `security/jwt/AgentServiceToken*`,
  `audit/AuditActions.java`'s `AGENT_ACTION_*` constants) is what JennySol's real AI agent talks to
  — see the JennySol repo's `PROJECT-PROGRESS.md` for the full evidence trail (real end-to-end
  verified, live, for both a read and an approved write, then withdrawn to leave no residue).
- **Recovery tags**: `m7-m11-m12-verified-2026-09-11`, `m8-m9-m10-verified-2026-09-11` — do not
  delete either.
- **Real, separate product gaps found in this repo's own code during this checkpoint** (not part
  of the AI integration, not fixed this checkpoint — see the full handoff doc for details):
  `LocalDiskFileStorageService` is not durable across redeploys (no Cloudinary, no Railway volume
  for the uploads directory) — real CVs/photos are lost on every redeploy today.
- **A real security fix landed this checkpoint**: `AgentServiceTokenVerifier` now pins to HS256
  only — it previously accepted an HS384-signed token using the correct secret. If you ever touch
  this class again, run `AgentServiceTokenVerifierTest`'s algorithm-confusion tests before and
  after your change.

Full details, ecosystem-wide architecture, all other repos, and the complete milestone/evidence
ledger: see `Jennysol-AI/VIKISOL-ECOSYSTEM-MAC-HANDOFF.md` and `Jennysol-AI/PROJECT-PROGRESS.md`.
