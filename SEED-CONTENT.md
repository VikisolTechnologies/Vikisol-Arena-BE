# SEED-CONTENT.md
### The on-demand demo-content overlay - ARENA-WEB-AND-SEED.md Part 4.

This is NOT the original `DataSeeder` (which runs once, automatically, on first
boot against an empty database - see `SeedDataFactory`/`IndianData`/`DataSeeder`
in `src/main/java/com/vikisol/arena/seed/`). This is a separate, on-demand,
labeled, fully-removable dataset - `DemoContentService`/`DemoContentController`
in the same package - built specifically so the new v3 UI screens can be
evaluated against realistic, TODAY-dated content instead of an empty app.

## Turning it on

Off by default, everywhere, on purpose. Set the Railway env var on `arena-api`:

```
ARENA_SEED_MODE=true
```

With it unset (or `false`), `DemoContentController`'s bean never registers -
`POST`/`DELETE /admin/demo-content` don't exist as routes at all, not just
"exist but refuse." Verified live: an authenticated platform_admin request to
either endpoint with the flag off surfaces Spring's own
`NoResourceFoundException` internally (confirmed via Railway logs) - the
genuine "no handler for this path" case, not a permission refusal.

## The two commands

Both require a `platform_admin` bearer token (sign in as `admin@vikisol.dev`,
see `TEST-LOGINS.md`).

**Create it:**
```bash
curl -X POST https://api-arena.vikisol.in/api/v1/admin/demo-content \
  -H "Authorization: Bearer <platform_admin token>"
```

Seeds ~12 activities + ~8 needs across Gachibowli/Gopanapally/Madhapur/Kondapur
(real Hyderabad IT-corridor neighborhoods, real jittered coordinates, `startsAt`
relative to the moment you run this - not stale like the original seeder's own
posts), 15 candidate profiles (one deliberately sparse, per Part 4.4's "thin
state" edge case), one lightweight demo company as FK backing for 4 job
postings + 2 projects (some with bids), 2 group rooms with messages (one with
a deliberately very long last message, per Part 4.4), and one notification of
each of the four types. Every row this creates has `demoContent = true`.
Calling this twice without removing first is a safe no-op (it checks for
existing demo posts before doing anything).

**Remove it completely:**
```bash
curl -X DELETE https://api-arena.vikisol.in/api/v1/admin/demo-content \
  -H "Authorization: Bearer <platform_admin token>"
```

FK-driven, not just `demoContent`-flag-driven - see
`DemoContentService.removeAll()`'s own comment for the exact dependency order.
This also cleans up anything a REAL account did against seeded content while
it was live (joined a demo activity, applied to a demo job, bid on a demo
project, followed a demo profile) - not just the rows the seeder itself
created.

## What's deliberately NOT covered

- **Company pages.** The one demo `EnterpriseProfile` this creates is FK
  backing for job postings/projects only - not the "~4 company pages with
  banners and posts" Part 4.3 describes. Company pages are out of scope for
  the current Phase 1 run (`ARENA-PHASE-1-BUILD.md` §8), and building that
  content without the screen that would display it isn't worth the risk of
  polluting real company-facing surfaces.
- **Direct-message / bid-thread conversations.** `Room` is 1:1 with `Post` in
  the current schema - only group/activity rooms are structurally possible
  today. That's a real, separate schema gap for Inbox (screen 5), not
  something to paper over with fabricated rows here.
- **Stock photography for candidate avatars.** `CandidateProfile` has no
  photo-URL field (only `avatarEmoji`) - seeded profiles get realistic emoji
  avatars, the same mechanism every real profile in this app already uses.
- **`FeedItemResponse.demoContent`.** The "Demo content" badge (arena-web)
  only shows on cards sourced from `GET /posts/nearby` (`PostResponse`) today.
  `GET /feed` (`FeedItemResponse`, `FeedAggregationService`) doesn't carry the
  flag yet - real, separate follow-up if the general feed also needs badging.

## Before a real launch

Run the removal command and verify `SELECT count(*) FROM arena_posts WHERE
demo_content = true` (and the other 34 tables `BaseEntity.demoContent` covers)
all read zero, then unset `ARENA_SEED_MODE` on Railway.
