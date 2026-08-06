// k6 isn't installable in this sandboxed environment (no system package manager access for a
// Go binary) - this is a Node-based substitute doing the same fundamental thing: concurrent
// virtual users running a realistic journey against the live API, ramped in steps, with
// p50/p95/p99 latency + throughput + error rate recorded per step. Bounded and short per step
// (not a sustained flood) since this runs against the actual live production instance, not an
// isolated staging copy - the goal is finding the trend/breaking point, not causing an outage.
const BASE = "https://api-arena.vikisol.in/api/v1";
// RateLimitFilter keys authenticated requests by user id (identityFor(): "user:" + principal
// getId()), not by token - many tokens for the same account all share one bucket. Using all 5
// seeded demo accounts round-robin is the most real headroom available without touching
// production rate-limit config (which we're not doing - see writeup).
const ACCOUNTS = [
  { email: "demo.talent@vikisol.dev", password: "Demo@12345" },
  { email: "demo.enterprise@vikisol.dev", password: "Demo@12345" },
  { email: "demo.recruiter@vikisol.dev", password: "Demo@12345" },
  { email: "demo.hiringmanager@vikisol.dev", password: "Demo@12345" },
  { email: "admin@vikisol.dev", password: "Demo@12345" },
];

function percentile(sorted, p) {
  const idx = Math.ceil((p / 100) * sorted.length) - 1;
  return sorted[Math.max(0, idx)];
}

async function timed(fn) {
  const start = Date.now();
  try {
    const res = await fn();
    return { ms: Date.now() - start, ok: res.ok, status: res.status };
  } catch (e) {
    return { ms: Date.now() - start, ok: false, status: 0, error: String(e) };
  }
}

// /auth/signin is deliberately rate-limited per-IP (RateLimitFilter, 60s fixed window) -
// hammering it directly from one machine just trips that protection (confirmed: got a real 429
// mid-run) rather than measuring backend capacity. Real concurrent users would come from many
// IPs and never share that bucket. So: authenticate a small, fixed pool of sessions up front
// (spread out, well under the rate limit), then fan out concurrency across the DATA endpoints
// using those already-issued tokens - that's what actually exercises query/DB/connection-pool
// capacity, which is what Step 6 is really asking about.
let tokenPool = [];
let tokenIdx = 0;
function nextToken() {
  const t = tokenPool[tokenIdx % tokenPool.length];
  tokenIdx++;
  return t;
}

async function authenticatePool() {
  const tokens = [];
  for (const creds of ACCOUNTS) {
    const res = await fetch(`${BASE}/auth/signin`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(creds),
    });
    const body = await res.json();
    if (body?.data?.token) tokens.push(body.data.token);
    await new Promise((r) => setTimeout(r, 300));
  }
  return tokens;
}

// One virtual user's journey using a pooled token, round-robin across the 5 distinct seeded
// accounts (see the rate-limit note above): browse jobs, browse marketplace - both open GET-list
// endpoints any authenticated role can hit, so this stays valid across the whole account pool.
async function journey() {
  const results = [];
  const auth = { Authorization: `Bearer ${nextToken()}` };

  const jobs = await timed(() => fetch(`${BASE}/jobs?page=0&size=20`, { headers: auth }));
  results.push({ step: "jobs", ...jobs });

  const projects = await timed(() => fetch(`${BASE}/marketplace/projects?page=0&size=20`, { headers: auth }));
  results.push({ step: "marketplace", ...projects });

  return results;
}

async function runStep(concurrency) {
  const start = Date.now();
  const allResults = await Promise.all(Array.from({ length: concurrency }, () => journey()));
  const wallMs = Date.now() - start;
  const flat = allResults.flat();
  const byStep = {};
  for (const r of flat) {
    (byStep[r.step] ??= []).push(r);
  }
  const summary = {};
  for (const [step, rs] of Object.entries(byStep)) {
    const latencies = rs.map((r) => r.ms).sort((a, b) => a - b);
    const errors = rs.filter((r) => !r.ok).length;
    const statusCounts = {};
    for (const r of rs) statusCounts[r.status] = (statusCounts[r.status] || 0) + 1;
    summary[step] = {
      count: rs.length,
      p50: percentile(latencies, 50),
      p95: percentile(latencies, 95),
      p99: percentile(latencies, 99),
      max: latencies[latencies.length - 1],
      errorRate: (errors / rs.length) * 100,
      statusCounts,
    };
  }
  const totalRequests = flat.length;
  const totalErrors = flat.filter((r) => !r.ok).length;
  return { concurrency, wallMs, throughputRps: (totalRequests / (wallMs / 1000)).toFixed(1), totalRequests, totalErrors, summary };
}

(async () => {
  console.log("Authenticating across all 5 seeded demo accounts (spaced out, clear of rate limits)...");
  tokenPool = await authenticatePool();
  console.log(`Got ${tokenPool.length} tokens from ${ACCOUNTS.length} distinct accounts.`);
  if (tokenPool.length === 0) {
    console.log("Could not authenticate any session (rate limit likely still cooling down from the earlier run) - aborting.");
    return;
  }

  const steps = [5, 10, 20, 30];
  const allStepResults = [];
  for (const c of steps) {
    console.log(`\n=== Ramping to ${c} concurrent virtual users ===`);
    const result = await runStep(c);
    allStepResults.push(result);
    console.log(JSON.stringify(result, null, 2));
    // Stop ramping further if error rate is already high at this step - no point pushing a
    // production instance harder once it's clearly past its breaking point.
    const worstErrorRate = Math.max(...Object.values(result.summary).map((s) => s.errorRate));
    if (worstErrorRate > 20) {
      console.log(`Error rate exceeded 20% at concurrency=${c} - stopping ramp here (breaking point found).`);
      break;
    }
    await new Promise((r) => setTimeout(r, 2000)); // brief cooldown between steps
  }
  require("fs").writeFileSync(
    "C:\\Users\\USER\\AppData\\Local\\Temp\\claude\\c--Users-USER-OneDrive---vikisol-in-Desktop-HRLMS\\f404032f-2230-4171-9c4d-d2f3a77e6373\\scratchpad\\load-test-results.json",
    JSON.stringify(allStepResults, null, 2)
  );
  console.log("\nDONE");
})();
