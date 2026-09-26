# Decisions

Choices made without the founder in the room. Newest first.

## 26 Sep 2026 — A service token missing its scope returns 403

Jenny's five writes stay the same JSON. The change is only what Arena answers when a verified service token is not allowed to make the call.

Previously the agent filter recorded the denial and continued. The request then looked unauthenticated, so the entry point returned 401. The contract test now calls `POST /posts` on the public API. A token whose scope does not include `arena.createPost` gets 403, and the filter does not call the rest of the chain.

A token for a different user is also 403. The case in the test is a company-admin subject that carries `arena.createPost`. `POST /posts` is talent-only, so that token cannot publish as the other person. A second talent token that only has `arena.joinActivity` is refused the same way.

Reads are not in the scope table. A service token on those paths is still recorded and then left to the normal rules. Forcing 403 there would break Jenny's search, nearby, communities, and jobs reads, which send the same bearer.

The filter reads the path from the servlet path. When that is blank, it uses the request URI with the context path removed. Production is `/api/v1` plus `/posts`. MockMvc was leaving the servlet path empty, so the same key has to be derived both ways.

## 26 Sep 2026 — Company-admin 2FA was never required

`AuthService` asks for a code only when the role is `COMPANY_ADMIN` or `PLATFORM_ADMIN` and `totpEnabled` is already true. The column defaults to false. The demo company admin is seeded that way. The setup, enable, disable, and verify endpoints exist. Nothing in this change turns enrollment on, because that would lock every current admin out of the session the test suite uses.

Recommendation: stop at the setup step, and do not issue a normal session, until those two roles finish enrollment. Do that in its own change, with the demo accounts enrolled first.
