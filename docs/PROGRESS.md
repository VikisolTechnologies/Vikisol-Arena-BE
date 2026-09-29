# Progress

Updated 28 Sep 2026. Resume from here.

## P0 release (production)

28 Sep 2026. Audit P0-1 and P0-2 are on `main` at `8ebccb6` (merge of `a504ef0`). Railway `arena-api` deployment `35679797` is SUCCESS and Online.

- Production env: `SEED_ENABLED=false`; platform-admin credentials set on a non-demo address; demo password env set and not a retired public value; admin 2FA uses the app default `true`.
- Boot lockdown rotated published demo passwords, marked seeded rows, disabled the demo platform admin, and excluded demo from public landing counts.
- `GET /api/v1/public/landing-stats` returns honest non-demo counts. Actuator health is UP.
- Frontend production is `4225f9e`. VNext PR #1 was not merged.

## Backend hardening (branch `cloud/api-hardening`, draft PR, not merged)

29 Sep 2026. Profile-id links from Talent Universe fixed, five N+1 lists batched, unbounded lists
paged (shape unchanged), V21 indexes, one error envelope with real statuses. Architect follow-ups:
provider errors never reach users (503 + short message, redacted server log), capped lists most
recent first, `X-Total-Count` / `X-Has-More` headers. 125 tests green (baseline 88, all still green). See `BACKEND-FIXES.md` and `API-CHANGES.md`. Needs review before
merge; merging to `main` deploys.

## Backend for the new frontend (branch `feature/be-fe-gaps`, draft PR stacked on `cloud/api-hardening`)

29 Sep 2026. Everything below is additive (migrations V22–V28, each with rollback in its header).
170 tests green, including the Jenny contract tests. Every endpoint is in `API-CHANGES.md` with its
gap number.

- Profile basics, FE gaps 1–5.
- Activities, G7–G13.
- Needs & offers, G14–G17.
- Career, G18–G21.
- Jobs, G22–G26.
- Business verification and team roles, G27–G28.
- Community projects and profile stats, G29–G32.

The frontend's `ARENA-APP-FLOW.md` does not exist, so the choices made without it are in
`DECISIONS.md`. Not merged; the architect reviews.

## Current step

No further P0 work. Frontend continues under Claude Code. Do not merge the VNext pull request.

## Done

- P0 production release (this section).
- STEP 1 cleanup is on frontend `main` at `5a1b52d`. `https://arena.vikisol.in/version` returned that commit. The isolated mobile paint budget held at 2.5s. The budget was not loosened.
- STEP 2: company-admin 2FA was never a forced enrollment. Auth was not changed. See `docs/DECISIONS.md` in this repo and `docs/SECURITY-FINDINGS.md` in the frontend repo.
- STEP 3: Jenny write bodies stay locked in `JennyArenaWriteBodyContractTest`. `JennyArenaWriteScopeContractTest` calls `POST /posts` with a service token that lacks `arena.createPost` and expects 403, and with a token for a company admin and expects 403.
- Sentry: `18f2dfcc8978170ff0cbd24b3ae2ef0c90eef296` scrubs events before send. `sendDefaultPii` is false. The DSN is only a Railway variable on `arena-api`.

## Next

Claude Code owns Arena frontend. Leave VNext unmerged and off production until founder approval.
