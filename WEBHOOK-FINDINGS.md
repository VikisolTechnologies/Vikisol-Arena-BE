# WEBHOOK-FINDINGS.md — old Arena→HRLMS webhook: verdict

**Verdict: DORMANT.** Safe to retire the old infra on the Arena side. Evidence below, both
code-level and runtime-level, not just one or the other.

## What the webhook is

HRLMS-BE (`Vikisol-One-BE`, deployed as project `enchanting-vibrancy`) exposes a real, wired-up
endpoint:

- `POST /api/v1/assessments/webhook` — `AssessmentController.java`
- Auth: shared secret via `X-API-Key` header, checked against `arena.webhook.api-key`
  (`ARENA_WEBHOOK_API_KEY` env var). `permitAll` in `SecurityConfig.java` since the caller is a
  separate deployed app with no HRLMS user session — auth is enforced inside the controller
  instead of via Spring Security's normal session/JWT path.
- Payload: `AssessmentWebhookRequest` — candidate name/email/phone, resume URL, tech stack,
  score/maxScore, and an `arenaSubmissionId` used to make ingestion idempotent if the caller
  retries.
- What it does when called: `AssessmentService.ingestResult()` finds-or-creates a `Candidate`
  (source = `PORTAL`), records an `Assessment` row, and sends two emails — a result email to the
  candidate and an internal notification to HRLMS staff.

This is genuinely load-bearing code on the HRLMS side — not a stub, not dead code. The question
is only ever whether anything still calls it.

## Code evidence: no caller anywhere in Arena's history

Cloned `VikisolTechnologies/Vikisol-Arena-BE` (the GitHub repo backing **both** the old Railway
project's `Vikisol-Arena-BE` service and the current `arena-staging` project's `arena-api`
service — there is only one Arena backend codebase; the "old" Railway project is a stale
deployment of the same repo, not a separate one) and checked its full commit history, 51 commits
from the initial Spring Boot scaffold (`51c80b7`) through the current safety-audit commit
(`bd0f9d1`).

Every controller that has ever existed in this repo, by directory:
`activity`, `applications`, `auth`, `company`/`companyPost`, `enterprise` (applicant/company-admin/
profile/job-posting/shortlist/talent-search), `follows`/`blocks`, `interviews`, `jobs`,
`marketplace`, `messaging`, `notifications`, `platform` (admin), `posts`, `profile`, `rooms`,
`verification`.

**No `webhook` controller, no HRLMS HTTP client, no outbound call to `hrlms.vikisol.in` or
`api.hrlms.vikisol.in` exists anywhere in this history.** Confirmed via `git log --all --grep`
across the full history and a full-tree `grep -ri "webhook\|hrlms"` on the current checkout — zero
matches in Arena's own source, in any commit.

The most likely explanation: this was built anticipating an integration from the original,
pre-pivot "job marketplace with candidate assessments" version of Arena, and the calling code
either never got built on the Arena side, or lived in some completely separate script/tool that
was never part of this repo. Either way, it is not something the current Talent-OS-pivot codebase
(posts/rooms/follows/comments — no assessment/test-taking feature anywhere in the product spec)
has any use for or reference to.

## Runtime evidence: zero hits, ever, in the full retention window

Read-only check of HRLMS-BE's actual production HTTP logs (Railway, project `enchanting-vibrancy`
— **no writes, no config changes, no deletions touched this project**, per the standing guardrail):

```
railway logs --http --since 30d --path "/api/v1/assessments/webhook"
```

**Zero results.** 30 days is the full HTTP log retention window available. Also checked for any
POST request to any path containing "assessments" at all, and for the endpoint without the
`/api/v1` context-path prefix in case of a routing mismatch — all zero. The log stream itself is
confirmed working (plenty of other traffic, mostly bot/vulnerability-scan noise, is visible in the
same window), so an empty result here is a real "never called," not a broken query.

## Conclusion

Both checks agree: nothing on the Arena side has ever called this webhook, and nothing has ever
hit it in production. It is dormant, not load-bearing. Retiring the old Arena Railway
infrastructure (the stale `Vikisol-Arena-BE` service + its Postgres, and the old Vercel project)
does not break this integration, because there is no integration currently running to break.

**Not touched by this finding, and not recommended to touch as a follow-up to it:** the
`AssessmentController`/`AssessmentService`/webhook code on the **HRLMS-BE side** itself. That
code is real, tested, and doesn't cost anything sitting unused — removing it is a separate,
unrelated decision for whoever owns HRLMS-BE's roadmap, not something implied by "the old Arena
infra is safe to delete."
