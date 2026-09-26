# Progress

Updated 26 Sep 2026. Resume from here. Do not redo the cleanup.

## Current step

STEP 3 of `docs/ARENA-MISSION.md` is on `main` at `ab6dcc6`. The contract tests passed before the fast-forward.

Frontend VNext continues on `feature/arena-vnext` in the worktree `~/Developer/arena-fe-vnext`. Do not merge that pull request.

## Done

- STEP 1 cleanup is on frontend `main` at `5a1b52d`. `https://arena.vikisol.in/version` returned that commit. The isolated mobile paint budget held at 2.5s. The budget was not loosened.
- STEP 2: company-admin 2FA was never a forced enrollment. Auth was not changed. See `docs/DECISIONS.md` in this repo and `docs/SECURITY-FINDINGS.md` in the frontend repo.
- STEP 3: Jenny write bodies stay locked in `JennyArenaWriteBodyContractTest`. `JennyArenaWriteScopeContractTest` calls `POST /posts` with a service token that lacks `arena.createPost` and expects 403, and with a token for a company admin and expects 403.

## Next

Continue the frontend mission from `docs/PROGRESS.md` on `feature/arena-vnext`: blueprint, mockups, private preview, report. Do not restart the shell.

## Open

- First JS on `/home` is still over 200KB gzipped. Do not hide the number.
- Sentry must allow `arena.vikisol.in`. Ignore Sentry's own 403 until then.
- The GitHub token cannot update `.github/workflows/e2e.yml` (no workflow scope).
