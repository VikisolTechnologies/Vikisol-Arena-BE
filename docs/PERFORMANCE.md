# Performance pass — 30 Sep 2026

Branch `feature/be-fe-gaps`. The task was:
- make nearby use an indexed location lookup;
- load-test feed, discover, nearby, jobs and messages with k6 at 50 concurrent users on a seeded database;
- aim for p95 under 300 ms;
- check the connection pool and the JVM memory for a single Railway instance;
- fix whatever is over target.

## Summary

- **Nearby** now finds its posts through a geohash-prefix index (`V38`) instead of reading the newest 500 open posts and filtering them in Java. It now also finds older posts, which the old read missed.
- **Throughput.** At 50 users on 2 vCPU, it went from **14.7 to 130–151 requests/s**, with no errors.
- **Latency at 50 users.** p95 went from **3.4–14.8 s to 0.3–0.66 s**. The median went from 0.2–5.6 s to **0.09–0.29 s**.
- **The target is not met at 50 users on 2 vCPU.** There the app is CPU-bound: 2 cores fully busy, and each request costs about 13–15 ms of CPU. Reaching the target there would take either more CPU or response-level caching (see "What is left").
- **Where the target is met:**
  - every endpoint is under 300 ms up to **40 users** on 2 vCPU (165 requests/s);
  - at 50 users with 3 cores for the app, six of the eight endpoints are under 300 ms.
- **Pool and JVM:**
  - Keep the pool at 10. It is now adjustable with `DB_POOL_SIZE`.
  - Heap: the live set is about 120 MB. The `Dockerfile` now defaults `JAVA_OPTS` to 60% of the container's memory, and the service gets 1 GB.
- **Follow-up (architect review, 30 Sep):** no extra CPU for launch.
  - Search now has trigram indexes (V40), which save about 12 ms per search.
  - Re-measured on a new, noisier host: every endpoint is under 300 ms at 30 users. At 40 users search is over (343 and 454 ms in two runs), and in the second run trending, feed and nearby were over too.
  - Scale-up trigger: real p95 over 300 ms, or about 30 concurrent users.

## Method

Everything ran inside the sandbox against throwaway local services with dummy values. Nothing connected to Railway or any real database, and no real secret was used.

| Setting | Value |
|---|---|
| Machine | 4 × Intel Xeon 2.8 GHz, 16 GB RAM |
| Database | PostgreSQL 14 (local), Redis (local) |
| App | `arena-api` jar, Java 21, `-Xmx512m`, pinned to **2 cores** with `taskset -c 0,1` (about the size of a small Railway service). Postgres and k6 share the other 2 cores. |
| Rate limits | Raised for the test only (`RATE_LIMIT_DEFAULT_PER_MIN`, `RATE_LIMIT_MESSAGING_PER_MIN`). 50 users share one IP, and each one reads messages far more often than the real 30-per-minute messaging limit allows. |
| Seed (`perf/seed.py`, `perf/load.sql`) | Talent across 5 cities, with profiles and geohashes: 2,000 people and 50 company admins. Companies: 50. Posts: 10,000 (60% activities, 20% needs, 10% updates, 5% offers, 5% collabs; 80% open; spread over 60 days). Joins: 19,634. Follows: 39,986. Jobs: 1,000. Conversations: 9,976, with 99,760 messages. |
| Load (`perf/load.js`, k6 0.54) | 50 signed-in users, run closed-loop: a 20 s ramp, a 90 s hold, then a 10 s ramp down. |
| One iteration per user | `GET /feed` (pages 0–2), `/posts/trending`, `/search?type=all` (a common word), `/posts/nearby` (a random city with jitter, 5 km), `/jobs`, `/jobs/{id}`, `/messages/conversations`, one thread; then 0.5–1.5 s of think time. |
| Warm-up | A 20 s run before each measured run. |
| Pass condition | p95 < 300 ms for each endpoint, and fewer than 1% failed requests. |

A closed-loop test sends more requests as the app gets faster: 50 users with about 1 s of think time between rounds of 8 requests. At the end that is 130–165 requests/s. That is the traffic of a few thousand people actively using the app, not 50.

**Runs of the same build vary by about ±15%.** The final build ran twice at 50 users, 2 cores and pool 10: 130 and 151 requests/s. The table shows the second run.

## Results

p95 in ms at 50 users, app on 2 cores. **Bold** means under 300 ms.

| Endpoint | Before | After N+1 fixes | + search in DB | Final |
|---|---|---|---|---|
| `GET /feed` | 14,760 | 1,320 | 1,040 | 393 |
| `GET /posts/trending` | 11,050 | 1,470 | 1,100 | 381 |
| `GET /search?type=all` | 10,600 | 1,940 | 1,420 | 496 |
| `GET /posts/nearby` | 6,890 | 1,490 | 1,040 | 421 |
| `GET /jobs` | 5,360 | 1,250 | 916 | 357 |
| `GET /jobs/{id}` | 4,640 | 1,050 | 886 | 341 |
| `GET /messages/conversations` | 3,650 | 1,030 | 794 | 333 |
| `GET /messages/conversations/{id}/messages` | 3,400 | 935 | 729 | **299** |
| Throughput (requests/s) | 14.7 | 62 | 78 | 151 |
| Failed requests | 0 | 0 | 0.01% | 0 |

Median in the final run: messages 84, conversations 100, job detail 106, jobs list 127, trending 143, feed 157, nearby 169, search 231 ms.

### Capacity: where the target holds

p95 in ms, final build.

| Users (app cores) | Feed | Trending | Search | Nearby | Jobs | Job | Conversations | Messages | requests/s |
|---|---|---|---|---|---|---|---|---|---|
| 20 (2) | **73** | **56** | **129** | **79** | **44** | **35** | **33** | **27** | 105 |
| 30 (2) | **117** | **90** | **187** | **121** | **73** | **59** | **58** | **47** | 147 |
| 40 (2) | **208** | **186** | **291** | **216** | **162** | **160** | **149** | **141** | 165 |
| 50 (2) | 393 | 381 | 496 | 421 | 357 | 341 | 333 | **299** | 151 |
| 50 (3)¹ | **281** | **276** | 366 | 304 | **252** | **237** | **226** | **207** | 182 |

¹ Postgres and k6 were squeezed onto the one remaining core. This row is indicative only.

On a quiet app, one warm request of each takes:

| Endpoint | Time |
|---|---|
| Conversations | 11 ms |
| Jobs | 15 ms |
| Trending | 25–30 ms |
| Nearby | 40 ms |
| Feed | 40–50 ms |
| Search | 55–85 ms |

## What was slow and what changed

Statements are SQL statements per request, counted with `pg_stat_statements`.

| Endpoint | Statements before | After |
|---|---|---|
| Feed | 1,946 | 32 |
| Search (`type=all`) | 2,283 | 15–37 |
| Trending | 95 | 18 |
| Nearby | 48 | 16 |

1. **Nearby: indexed lookup.**
   - `V38__nearby_geohash_index.sql` adds a partial index on `arena_posts (geohash COLLATE "C") WHERE status = 'OPEN' AND geohash IS NOT NULL`. `COLLATE "C"` lets Postgres use it for prefix ranges.
   - `GeohashUtil.coverCells` picks the finest precision (7 down to 1) whose cells cover the circle's bounding box in at most 25 cells.
   - `PostGeoRepository.findOpenInCells` reads those cells as one OR of prefix ranges; the plan is a Bitmap Index Scan. It returns only joinable, non-anonymous kinds, newest first, capped at 2,000 candidates. The exact Haversine filter and the distance sort are unchanged.
   - The stored approximate point is the centre of the stored geohash cell, so every post inside the circle lies in a covered cell. `GeohashCoverTest` checks 400 random circles with 200 points each.
   - Checked against a brute-force SQL distance count on the seed data: 75/75, 11/11, 94/94 and 0/0.
   - **Behaviour change:** the old read looked at the newest 500 open posts anywhere, so an older post nearby could be missed. `DiscoveryReadsTest` puts one behind 520 newer ones, and it is now found.
2. **N+1 removed from list mapping.**
   - The viewer's blocks are now read as one set, not two queries per post.
   - Join status and room id are batched per page.
   - Feed candidates are fetched with their authors.
   - Company cards batch their counts.
   - Jobs and projects map in batches.
3. **Feed maps only its page.** Before, it mapped all 500 candidates plus 200 jobs and 200 projects to build a 20-item page. Now it ranks light rows and maps only the page.
4. **Search reads light rows.**
   - Posts are scored from a projection (`PostSearchRow`: text fields and author), and only the hits are loaded.
   - When the first word is plain ASCII, the database first narrows the rows to those containing it. Postgres and Java lower-case ASCII the same way, so nothing that could match is dropped.
   - Jobs work the same way (`JobPostingRepository.searchCandidates`).
   - With `type=all`, activities and discussions come from one read. The shared window is 2,000 rows per kind. It only differs from separate reads when one word matches more than that many live posts.
   - A per-post regex in the ranking was replaced by an equivalent word-boundary scan. `SearchTextTest` checks it against the old regex.
5. **Shared candidate window for feed and trending (`FeedWindowCache`).**
   - What is shared:
     - the newest 500 open posts as light rows (embedding already decoded; report, comment and reaction counts already counted);
     - the feed's open jobs and projects as ready-made items.
   - These are shared by all requests for `app.feed.window-cache-seconds` (default 5, `FEED_WINDOW_CACHE_SECONDS`; 0 turns it off).
   - Any write to a post, job or project clears the cache once its transaction commits, through a JPA entity listener. A new, edited, closed or deleted item shows on the next request (checked end to end against the load-test app).
   - A load that overlaps a clear is not stored.
   - What is computed per viewer: follows, blocks, the interest vector and the score.
   - The page's posts are read fresh, and a post closed in the meantime is left out.
   - **Behaviour change:** comment, reaction, report and bid counts can lag in *ranking* for up to 5 s. The counts shown in responses are always fresh.
   - Tests turn the cache off, because their transactions roll back and never commit. `TtlCacheTest` covers the cache itself.
6. **JWT verified once per request.** The filter used to check the signature four times per request. The parser is now built once, and the same checks run once (`JwtTokenProvider.sessionClaims`).

Tried and dropped:
- `hibernate.query.in_clause_parameter_padding`: no measurable change.
- `hibernate.criteria.plan_cache_enabled`: not available in Hibernate 6.5.2.

## Connection pool

HikariCP had no settings, so it used its default of 10. It is now `spring.datasource.hikari.maximum-pool-size: ${DB_POOL_SIZE:10}`.

Measured at 50 users on 2 cores:

| Pool | requests/s | Search p95 | Feed p95 | Conversations p95 |
|---|---|---|---|---|
| 5 | 167 | 407 | 327 | 291 |
| 10 | 151 | 496 | 393 | 333 |
| 20 | 155 | 486 | 348 | 204 |

These differences are within the run-to-run noise. The app is CPU-bound, not connection-bound. Thread dumps show 20–30 threads waiting for a connection while 8 of the 10 are "idle in transaction": they belong to threads doing Java work while the CPU is busy.

A bigger pool doesn't add CPU. **Keep 10** for one instance. Railway Postgres allows far more connections, so 10 per instance also leaves room for a second instance.

`spring.jpa.open-in-view` is on (Spring's default), so a request holds its connection until the response is written. Turning it off would free connections sooner, but any lazy load outside a service transaction would then fail. That is a separate change with its own tests.

## JVM memory

Measured under load with `-Xmx512m`:
- **Heap:** live data after a young GC is about 118 MB, and the young generation fills to about 307 MB.
- **GC:** a young pause every 0.6 s, 9–13 ms each, which is under 2% of the time. There were no full GCs.
- **Memory outside the heap:** metaspace is 135 MB, and the process RSS is about 720 MB.

The `Dockerfile` used to set `JAVA_OPTS=""`. With no flags, Java 21 in a container caps the heap at 25% of the container's memory: 512 MB on a 2 GB service, or 128 MB on a 512 MB one.

Architect decision (30 Sep): the `Dockerfile` now defaults to:

```
JAVA_OPTS=-XX:MaxRAMPercentage=60 -XX:+ExitOnOutOfMemoryError
```

The founder sets `arena-api` to 1 GB in Railway. A `JAVA_OPTS` variable on the Railway service still replaces the default. `railway.toml` is unchanged.
- On a 1 GB service that is about a 600 MB heap. The rest covers metaspace, threads and buffers.
- Under 768 MB, RSS runs close to the limit.
- `ExitOnOutOfMemoryError` makes Railway restart a broken JVM instead of leaving it half-alive.
- The default G1 collector is fine at this heap size.

## Follow-up after the architect's review (30 Sep)

**Decision: no extra CPU and no second instance for now.** 30 to 40 concurrent users under 300 ms is enough for the Gachibowli launch.

**Search trigram index (`V40__search_trigram_indexes.sql`, additive).**
- **Indexes:** GIN `pg_trgm` indexes on:
  - the posts' lowercased title, body and place, as one text;
  - people's names;
  - company names.
- **Query:** search now reads its candidate rows in one plain SQL query (`PostSearchRepository`), whose LIKE conditions each use one of those indexes. `EXPLAIN` shows a Bitmap Index Scan on `idx_posts_search_text_trgm`.
- **Results unchanged:** search words never contain a space, so matching the joined text is the same as matching each field. `DiscoveryReadsTest` checks that `type=all` still equals the separate searches, and that the indexes exist.
- **Safe to deploy:** if the database role may not create the `pg_trgm` extension, the migration skips the indexes with a notice and search keeps working the slower way.
- **Cost:** on the seeded database both migrations (V39 and V40) applied in 0.9 s.

**Re-measure.** After the worker restart this session ran on a different sandbox host (a new kernel build). There, even the endpoints this change doesn't touch were about 20% slower than the day before, so the numbers below are not comparable with the tables above.
- **Search with and without the indexes**, same build, back to back, median of 40 warm sequential requests:

| Search | Without trigram indexes | With |
|---|---|---|
| a rare word ("zebra") | 30 ms | 18 ms |
| a word in 1 in 5 posts ("chess") | 67 ms | 55 ms |
| a job word ("engineer") | 48 ms | 37 ms |
| a word in every author's name ("person") | 107 ms | 101 ms |
| control: `/messages/conversations` | 13 ms | 12 ms |

- **k6 on the final build**, p95 in ms, app on 2 cores. **Bold** means under 300 ms.

| Users | Feed | Trending | Search | Nearby | Jobs | Job | Conversations | Messages | requests/s |
|---|---|---|---|---|---|---|---|---|---|
| 30 | **147** | **130** | **241** | **171** | **107** | **89** | **90** | **75** | 142 |
| 40 | **256** | **235** | 343 | **270** | **216** | **202** | **183** | **181** | 153 |
| 50 | 601 | 576 | 708 | 639 | 528 | 497 | 473 | 452 | 116 |

A second 40-user run on this host, minutes earlier, was slower: search 454, nearby 365, trending 336 and feed 327, with the rest under 300. Run-to-run noise here is larger than the day before. Search is the endpoint closest to the limit: its load-test words are common, each matching up to a fifth of all posts, so each request still ranks up to 4,000 candidate rows.

**Scale-up trigger.** Add CPU (or a second instance) when either happens in production:
- the real p95 of any endpoint above goes over 300 ms;
- concurrent active users reach about 30.

Watch both from Railway's metrics or the request logs. On today's host, 30 closed-loop users is the last point where every endpoint was under 300 ms.

**Messaging reads.** The conversation list and threads (GET) now have their own limit of 60 per minute per user (`RATE_LIMIT_MESSAGING_READ_PER_MIN`). Sending stays at 30 per minute. The frontend polls:
- the open thread every 5 s, only while it is visible (12 per minute);
- the conversation list every 30 s (2 per minute).

## What is left, in order of value

1. **More CPU, or a second instance,** when the scale-up trigger above fires. By the sweep, 3–4 vCPU holds 50 closed-loop users.
2. **Search.**
   - Rare words are now found through the trigram indexes.
   - Common words still return many rows to rank in Java. Postgres full-text search with ranking in SQL is the real fix, and `SearchText`'s comment already expects that swap.
3. **Per-request fixed cost.** On every request:
   - `loadUserByUsername` reads the user from the database;
   - two Redis calls run (rate limit and token denylist);
   - Spring Data derived queries are re-translated by Hibernate on every call, since 6.5 does not cache Criteria plans.

   Upgrading to Hibernate 6.6+ with `hibernate.criteria.plan_cache_enabled`, and a short-TTL principal cache, would each cut a few ms.
4. **Short-TTL response caching** for guest or anonymous feed, trending and jobs pages, if traffic grows before CPU does.

## Reproducing

1. Run `perf/seed.py` to write the CSVs.
2. Apply the Flyway migrations to an empty local Postgres, then run `psql -f perf/load.sql` from the CSV directory.
3. Start the jar with dummy env values: a local `DB_URL`, a throwaway `JWT_SECRET`, and raised rate limits (`RATE_LIMIT_DEFAULT_PER_MIN`, `RATE_LIMIT_MESSAGING_PER_MIN`, `RATE_LIMIT_MESSAGING_READ_PER_MIN`).
4. Mint an HS256 token for each of the first 50 people with that throwaway secret, using the claims `JwtTokenProvider` issues. `perf/mint.py` refreshes an existing `tokens.json` for 24 hours. Access tokens last 15 minutes in the app, so mint them just before a run.
5. Run `VUS=50 HOLD=90s k6 run perf/load.js`.
