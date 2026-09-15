# DEMO-ACCOUNTS.md
### 50 demo accounts, seeded through the real signup/invite paths - ARENA-FINISH-IT.md §1.1.

Created by `POST /admin/demo-content` (gated behind `ARENA_SEED_MODE`, see `SEED-CONTENT.md`),
removed completely by `DELETE /admin/demo-content`. Every account is flagged `demoContent = true`
at the database level and is disabled entirely when `ARENA_SEED_MODE` is off - the seed endpoints
don't exist as routes without it, so there is no path that creates these accounts by accident.

**Shared password for all 50: `ArenaDemo2026!`**

Demo accounts cannot initiate anything involving money - no payment or payout path in this
product accepts a `demoContent` account as either payer or payee (that gate lives in the real
payment/payout services, not here; this file only lists accounts, it doesn't grant capabilities).

## Start here

| Account | Why |
|---|---|
| `user01@demo.arena.test` | Busy talent profile - real skills, precise location (Gachibowli area), among the accounts with the most posts/comments/reactions attached. |
| `user41@demo.arena.test` | Company admin, "Preview Labs" - the primary demo tenant: full team (5 recruiters + 1 hiring manager invited through it), 20 job postings, 2 of the 12 projects. |
| `user44@demo.arena.test` | Recruiter at Preview Labs - real invite-then-accept account, not a signup. |
| `user40@demo.arena.test` | Deliberately sparse talent profile - no skills, no bio, minimal experience. The thin-state test case. |

## All 50 accounts

| # | Email | Role | Note |
|---|---|---|---|
| 01-39 | `user01`-`user39@demo.arena.test` | talent | Real signups. Skills/industry/experience/location randomized per account (Indian names, realistic Hyderabad neighborhoods). First 25 (`user01`-`user25`) have precise location consent set, spread across Gachibowli/Gopanapally/Madhapur/Kondapur/Hitec City, so Home/Map's nearby query has real density everywhere. |
| 40 | `user40@demo.arena.test` | talent | **Deliberately sparse** - no skills, no bio, default experience. The onboarding/thin-state test case. |
| 41 | `user41@demo.arena.test` | company_admin | Real signup. Admin of **Preview Labs** (engineering) - the primary demo tenant, 10 seats, full invited team. |
| 42 | `user42@demo.arena.test` | company_admin | Real signup. Admin of **Northstar Design Co** (design). |
| 43 | `user43@demo.arena.test` | company_admin | Real signup. Admin of **Meridian Health Partners** (healthcare). |
| 44-48 | `user44`-`user48@demo.arena.test` | recruiter | Real invite -> accept flow, all against Preview Labs. |
| 49 | `user49@demo.arena.test` | hiring_manager | Real invite -> accept flow, against Preview Labs. |
| 50 | `user50@demo.arena.test` | platform_admin | **No self-service path exists for this role anywhere in this product, by design** - `AuthService.signUp()` refuses it outright and there is no invite flow for it either. Created the same direct way the original `DataSeeder`'s own one platform_admin account (`admin@vikisol.dev`) already is. Not a "real path" fiction - genuinely the only way this role is ever created. |

Two more companies exist as content backing (job postings/projects need *some* tenant) but have
no dedicated login, since inventing one nobody asked for felt worse than being short two logins:
**Techolution** and **Swiggy** (names/emoji from the existing `IndianData` seed pool, industries
randomized).

## What's seeded alongside the accounts

- 24 activity posts + 24 needs across Gachibowli, Gopanapally, Madhapur, Kondapur, Hitec City - varied
  capacities, join counts (0 to full), visibility (public/approval), real geo-jittered coordinates.
- ~60+ comments at uneven density, reactions and saves scattered unevenly.
- 4 group activity rooms with real message threads (one with a deliberately very long last message).
- 4 direct-message / "bid thread" style conversations (`Conversation`/`ThreadMessage` - separate
  from the group `Room` mechanism above).
- 20 job postings across the 5 companies, 15 applications across the real pipeline stages
  (applied/screening/interview/offer/rejected), with interview slots proposed for the ones at
  interview/offer stage.
- 12 projects open for bidding, 0-6 bids each, the first 2 fully resolved (won/lost bids, real
  milestone splits) so both outcome states are visible.
- 4 notifications (one of each type: agent, interview, bid, system) on `user01`.
- ~15 follow relationships among the talent accounts.

**Edge cases, deliberately included:** one very long need post, one activity with zero joins, one
room's last message is deliberately very long, one profile (`user40`) is deliberately sparse.

## Removing everything

```bash
curl -X DELETE https://api-arena.vikisol.in/api/v1/admin/demo-content \
  -H "Authorization: Bearer <platform_admin token>"
```

FK-driven removal (see `DemoContentService.removeAll()`), so it also cleans up anything a real
account did against seeded content while it was live - joined a demo activity, applied to a demo
job, bid on a demo project, followed a demo profile, messaged a demo recruiter - not just the rows
the seeder itself created.
