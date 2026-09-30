# Progress

Updated 29 Sep 2026. Resume from here.

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

29 Sep 2026. Everything is additive: migrations V22–V37, each with rollback in its header.
210 tests green, including the Jenny contract tests. Every endpoint is in `API-CHANGES.md` with
its gap or FE-API-GAPS row number.

- First pass:
  - profile basics (FE gaps 1–5);
  - activities (G7–G13);
  - needs & offers (G14–G17);
  - career (G18–G21);
  - jobs (G22–G26);
  - business verification and team roles (G27–G28);
  - community projects and profile stats (G29–G32).
- Architect review:
  - candidates may only withdraw (item 2);
  - export and erasure cover every new table (item 4, legal);
  - join-with-answers is documented for Jenny's gateway (item 5);
  - "nearby only" stays unbuilt (item 3).
- Built from `ARENA-APP-FLOW.md` and the 41 FE-API-GAPS rows:
  - activities, post edit and pause, needs, career, jobs, company verification with an admin
    queue, projects;
  - people-app rows: evidence, notifications, people search, visibility, connect requests, inbox
    preview;
  - the admin disputes queue.
- `docs/GAP-MAPPING.md` maps every row: 35 built, 1 partly (row 27: no radius), 5 not built with
  reasons (rows 6, 9, 24, 35, 40). Where the flow doc differed from the first build, it won; see
  `DECISIONS.md`.

Not merged; the architect reviews.

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
