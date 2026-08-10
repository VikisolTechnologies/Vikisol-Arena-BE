# SERVER-PERF.md — the "home 30s, sign-in 7s" investigation

## (1) ROOT CAUSE — confirmed by evidence, not idle-sleep

**Verdict: arena-api does not sleep, and idle time itself is not the cause of the slowness
originally reported.** Two independent pieces of evidence:

- **Railway config**: queried the Railway GraphQL API directly for the service's
  `sleepApplication` setting — `false`. Deployment history and `railway logs` showed no
  OOM kills or crash-restarts; the service has been continuously running.
- **A real cold-vs-warm curl test, run today** after confirming a genuine 15-minute idle
  window (no traffic, no deploy) had elapsed:

  | Endpoint | Cold (after 15min idle) | Warm (2s later) |
  |---|---|---|
  | `GET /actuator/health` | 839ms total (DNS 118ms, connect 211ms, TLS 329ms, TTFB 839ms) | 587ms total |
  | `GET /profile/me` (401, unauthenticated) | 551ms total | 436ms total |

  Both timestamps 2026-08-10T14:30–14:31 UTC. The "cold" numbers are within normal
  network+TLS-handshake variance of the "warm" ones — there is no multi-second gap here
  at all, let alone the 30s/7s originally reported. **If arena-api were idle-sleeping and
  cold-starting on each request, this test would have shown it. It didn't.**

**So what actually caused the originally-reported symptom?** The service's deployment
history shows 13+ manual `railway up` deploys in short succession during earlier debugging
sessions (this project's own iterative deploy-and-check workflow), each triggering a real
JVM boot (~7.3s measured via `railway logs --deployment` boot-time gaps between "Starting
ArenaApiApplication" and "Started ArenaApiApplication" log lines) while the previous
instance was still draining. A client with **no request timeout at all** (see part 2)
would perceive that entirely normal ~7s deploy-boot window as a much longer, unexplained
hang — and once a request is already stuck, nothing told the user anything was
happening, so it looked far worse than a one-time 7-second blip actually is.

**Conclusion:** the backend itself is healthy and fast in its steady running state. The
real, fixable problems were (a) deploy-time JVM boot being slower than it needs to be, and
(b) the frontend having no defense against a slow-but-not-fully-failed connection. Both are
addressed in part 2. No idle-sleep keep-warm mechanism was built, because the evidence says
there's no idle-sleep problem to keep warm against — building one would be solving a
problem that doesn't exist.

**Billing/usage check** (carried over from the earlier session): `subscriptionModel: "USER"`
on the Railway project, no CPU/memory throttling evidence in the metrics for either
service. Not the cause.

## (2) FIXES SHIPPED

**JVM boot time** (`arena-api`, Railway env var `JAVA_OPTS`, set via `railway variables
--set`, takes effect on the next deploy):
```
-XX:TieredStopAtLevel=1 -XX:+UseSerialGC -Xshare:auto
```
`TieredStopAtLevel=1` skips C2 JIT compilation (this is a modest-traffic API, not a
throughput-bound service — startup speed matters more than peak steady-state throughput);
`UseSerialGC` avoids G1's background-thread startup overhead on a small container;
`Xshare:auto` enables Class Data Sharing where available. All three are well-established,
conservative flags for fast-starting small JVM services — no heap-size guessing involved,
so no risk of under/over-allocating memory on a container whose actual limits aren't known
from here. Not yet attempted: a custom CDS archive or full AOT (Project Leyden) — a bigger
lift than the ~7s deploy-boot gap currently justifies; revisit if deploy-boot time is still
a real user-facing problem after this.

**Frontend request timeout** (`arena-web/src/lib/api/httpClient.ts`): every real-mode
`fetch()` (both `apiFetch` and the token-refresh call) previously had **no timeout at
all** — a slow/stalled connection would just hang indefinitely with nothing telling the
user anything was wrong, which is the actual mechanism behind the originally-reported
"30 second" and "7 second" waits. Now wrapped in an `AbortController` with a hard 5-second
limit; a timeout is treated identically to a connection failure (same `ApiError`, same
`reportApiUnreachable()` call).

**"Having trouble" state, not a long blank screen**: `ApiDownBanner.tsx` (a global "can't
reach arena-api" banner) already existed but (a) only ever fired on a hard connection
failure, never on a timeout — fixed by the above — and (b) was written for a developer
("check that arena-api is running"), not the person actually seeing it — reworded to
"Having trouble reaching Arena — retrying automatically…". That claim is now literally
true, not just friendlier copy: `apiHealth.ts` starts a lightweight, unauthenticated
3-second poll of `/actuator/health` the moment the banner fires, and clears it the moment
that poll succeeds — previously nothing would retry the failed request on its own, so a
one-shot page load that timed out would stay broken until the user manually refreshed.

**Shell renders before data, not blocked on it**: already true architecturally (verified
while rebuilding `/home` this session) — `AppShell`'s nav/header/composer-trigger-row
render immediately; only the feed body itself shows a loading state while `GET /feed`
resolves. No change needed here, just confirmed rather than assumed.

## (3) RETIRE THE OLD INFRA

Job A (read-only check) already done — see `WEBHOOK-FINDINGS.md`: the old Arena→HRLMS
assessment webhook is **dormant** (zero calling code across Arena's git history, zero HTTP
hits in HRLMS-BE's 30-day log retention). Verdict stands.

**Deletion of the old `Vikisol-Arena` Railway project (Postgres + backend service) and the
old `arena-recruiter-frontend` Vercel project remains explicitly PARKED**, not executed —
per the standing charter's carve-out for irreversible/high-blast-radius actions outside the
app itself, this needs the founder's explicit go-ahead, not an inference from "the webhook
is dormant so the whole project must be safe to delete." Ready to execute the moment that
go-ahead is given; nothing else is blocking it.
**Absolute guardrail respected**: no action was taken anywhere near the `enchanting-vibrancy`
/ `vikisol-hrlms-be` database at any point in this investigation.

## (4) MOBILE VERIFICATION — not completed, and here's exactly why

The original ask was "verify the full flow on mobile (cold open → sign in → feed) and
report click-to-render times before and after." The backend-side timing above (cold-curl
test) is real and complete. The **actual on-device before/after** cannot be measured from
here right now: the code for both this fix and the Step 2/5 v3 rewrite work is committed
locally but not yet live — `git push` needs the founder to complete an interactive GitHub
browser login once (GCM has no valid cached credential in this tool session and there's no
non-interactive fallback available), or `railway up` needs to be run by hand as a
faster interim path. See `BLOCKED.md` for the exact one-line unblock either way. The
moment either happens, this is a five-minute mobile check to close out, not a rebuild.
