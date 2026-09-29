# Decisions

Choices made without the founder in the room. Newest first.

## 29 Sep 2026 — Backend for the new frontend (`feature/be-fe-gaps`)

The brief pointed at `docs/design/ARENA-APP-FLOW.md` in the frontend repo. That file does not
exist on any frontend branch, and `docs/FE-API-GAPS.md` lists only gaps 1–6. The six areas were
built from `docs/design/BPLUS-SCREENS.md` and the frontend `ARENA-MISSION.md`, and numbered G7 and
up in `API-CHANGES.md`. Choices made along the way:

- **Everything hangs off existing rows.** Activities, needs and offers are still posts created with
  `POST /posts`; the new tables key on the post. The Jenny write bodies are unchanged, and their
  contract tests stay green.
- **Host questions vs. the Jenny join.** `POST /posts/{id}/joins` can't carry answers, so an
  activity whose host added a *required* question refuses it and points to
  `POST /activities/{id}/join`. No existing activity has questions.
- **Attendance never becomes reputation unchecked** (protocol §4). A host's no-show lowers the
  public join count only after 72h with no dispute. An open dispute never counts against the
  participant, and the host can accept it but not reject it, so an unresolved dispute stays in the
  participant's favour.
- **The private coordination room for a need is the pair's existing DM**, not a second messaging
  system (mission §5). Anonymous needs can't take responses, because accepting one would open a
  chat that reveals the author.
- **The need "Share with: nearby only" scope is not built.** Feed and search don't filter by it,
  and storing a privacy promise the API doesn't keep would be dishonest.
- **One pay rule.** `CompensationPolicy` also governs the existing `currentCtc`/`expectedCtc`, so
  "private by default" is true everywhere: a candidate without a career profile shows no pay to
  employers. The applicant list also stopped returning the approximate home location, which talent
  search already stripped.
- **Found, not changed:** `PUT /applications/{id}/stage` lets a candidate set any stage on their own
  application, `offer` and now `hired` included. The frontend may rely on it, so it is logged for
  the architect rather than changed here. Suggested fix: candidates may only withdraw.

## 29 Sep 2026 — Architect follow-ups on `cloud/api-hardening`

Decided by the architect; the implementation choices below are mine.

- A failed email/SMS/WhatsApp/Teams/OpenAI call answers **503** with a short message. The provider's
  text is logged server-side only, redacted. 503 rather than 502 because the user's next step is
  "try again in a minute", which is what 503 means to clients and proxies.
- Jenny's unavailable reply keeps its mandated copy ("The agent is temporarily unavailable. Your
  Arena account is still working normally.") instead of a reword. It never contained provider text.
- `/rooms` now sorts by latest activity, not join time, so the 100-row cap never hides a busy room.
  Jenny history pages are chosen newest first but read top to bottom inside a page.
- Join requests stay oldest first (a queue), and communities keep their member-count ranking.
- Paging state is in `X-Total-Count` / `X-Has-More` headers, exposed through CORS. The body stays
  an array.

## 29 Sep 2026 — Error pages are not re-authenticated; list endpoints cap at 100 rows

Branch `cloud/api-hardening`, not merged. Two security-config lines changed, logged here as the
mission asks for any auth-adjacent change.

- The servlet ERROR dispatch is `permitAll`. It only renders the status of a request that already
  failed (`ApiErrorController`, status only, no message or path). Before, a guest request that
  failed after security had run was re-checked on the error dispatch and came back as 401, hiding
  the real error. No endpoint's access rule changed.
- 403s decided by the security filter chain now write `{"success": false, "message": "Access
  denied"}` instead of an empty body. Who gets a 403 is unchanged.

List endpoints that returned every row (followers, following, blocks, rooms, DM conversations,
join requests, communities, Jenny chat history) now return the first 100 by default and accept
`page`/`size`. Their JSON stays a bare array, so neither the frontend nor the Jenny gateway's
`/communities` read changes shape. Details: `API-CHANGES.md`, `BACKEND-FIXES.md`.

## 26 Sep 2026 — A service token missing its scope returns 403

Jenny's five writes stay the same JSON. The change is only what Arena answers when a verified service token is not allowed to make the call.

Previously the agent filter recorded the denial and continued. The request then looked unauthenticated, so the entry point returned 401. The contract test now calls `POST /posts` on the public API. A token whose scope does not include `arena.createPost` gets 403, and the filter does not call the rest of the chain.

A token for a different user is also 403. The case in the test is a company-admin subject that carries `arena.createPost`. `POST /posts` is talent-only, so that token cannot publish as the other person. A second talent token that only has `arena.joinActivity` is refused the same way.

Reads are not in the scope table. A service token on those paths is still recorded and then left to the normal rules. Forcing 403 there would break Jenny's search, nearby, communities, and jobs reads, which send the same bearer.

The filter reads the path from the servlet path. When that is blank, it uses the request URI with the context path removed. Production is `/api/v1` plus `/posts`. MockMvc was leaving the servlet path empty, so the same key has to be derived both ways.

## 26 Sep 2026 — Company-admin 2FA was never required

`AuthService` asks for a code only when the role is `COMPANY_ADMIN` or `PLATFORM_ADMIN` and `totpEnabled` is already true. The column defaults to false. The demo company admin is seeded that way. The setup, enable, disable, and verify endpoints exist. Nothing in this change turns enrollment on, because that would lock every current admin out of the session the test suite uses.

Recommendation: stop at the setup step, and do not issue a normal session, until those two roles finish enrollment. Do that in its own change, with the demo accounts enrolled first.
