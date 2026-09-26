# Arena backend agent entry point

Before changing code, read in this order:

1. `docs/VIKISOL-MASTER-CONTEXT.md`
2. `docs/AGENT-COLLABORATION-PROTOCOL.md`
3. `docs/ARENA-MISSION.md`
4. The Arena frontend's `docs/PROGRESS.md` and `docs/ARENA-CURRENT-STATE.md`
5. `jennysol-ai/docs/JENNY-ARENA-CONTRACT.md` when an agent-facing endpoint is involved

Cursor owns Arena frontend and backend implementation during the current parallel mission. Claude
Code owns JennySol. Coordinate cross-product work through the published contract and tests; do not
edit JennySol implementation from this repository.

Backend changes must be additive, authorization-safe, migration-safe, and covered for ownership,
invalid state, retries/idempotency, and concurrency where relevant. Run `./mvnw test` before every
pushed batch and record the exact result and commit SHA in the shared Arena progress file.
