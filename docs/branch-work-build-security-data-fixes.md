# Work on `build-security-data-fixes`

- Recorded: 2026-09-30
- Base: `e01053e` (`main`)
- Implementation covered: through `52653a1`
- Integration status: pushed to Gitea; not merged into `main`.

This branch addresses findings from the [2026-09-29 project audit](audit/project-audit-2026-09-29.md). The audit's original findings describe the baseline at `e01053e`; its 2026-09-30 follow-ups describe the branch changes.

## Implemented

| Area | Work completed | Key commits |
|---|---|---|
| Build and configuration | Repaired the PowerShell Maven Wrapper path handling, the duplicate user-service security configuration, and the resulting startup wiring failure. | `bd827e8` |
| Authentication and identity | Persisted issued and revoked sessions and provisioned identities in the infrastructure profile. Added protected token validation used by the gateway and business services. Synchronized user email, role, and enabled-state changes with auth and revoked affected sessions. Made identity updates and session revocation atomic within the auth database. | `03a8cc0`, `bfc0f65`, `30ca278`, `66d58fa` |
| Travel data | Made Neo4j the infrastructure-profile source of truth for destinations, complete itineraries, and participants. Added graph transactions and cleanup of travel-owned nodes while preserving shared destinations. Documented handling for incomplete legacy travel records. | `eb8dc1e`, `6e235ba` |
| Payment data and providers | Added PostgreSQL-backed gateway and payment-method metadata in the infrastructure profile. Stripe and PayPal adapters now inspect supplied sandbox references and perform provider cleanup calls. Added pending, active, failed, and deletion-pending state handling, safe failure codes, idempotency, and concurrency controls. | `aab107c`, `a9cf6da`, `5e4f802`, `f2d3433` |
| Cross-service user deletion | Persisted user-deletion progress and coordinated auth revocation, payment-method cleanup, travel participation cleanup, and traveler deletion. Added tombstones to prevent new connections or participation after cleanup begins. Pending provider work returns `202` instead of reporting completion. | `02a0b2c` |
| Admin dashboard | Replaced the summary screen with user/traveler, destination/travel/itinerary, gateway, and payment-method workflows. Added a shared API client, login/logout and expiry handling, responsive forms, status/error messages, and retry-safe payment actions. | `52653a1` |

The design and implementation plans for these changes are in [`docs/superpowers`](superpowers/). The dashboard uses the **implemented controller payloads**; some planned OpenAPI fields and operations are not yet available.

## Verification recorded so far

- Earlier in this branch, `mvnw.cmd -B -ntp verify` passed with 57 tests after the auth work and 61 tests after the travel work. These runs **predate** the later payment, deletion, and dashboard changes; they are not a full-suite result for the branch tip.
- Base, secure, and scale Compose configuration checks passed after the auth and travel changes. This confirms manifest resolution, not a running deployment.
- The payment and cross-service deletion changes have code and compile evidence only. No credentialed Stripe/PayPal sandbox run or deployed PostgreSQL/Neo4j lifecycle run has been recorded.
- `npm --prefix admin-ui run build` passed for the dashboard at `52653a1`, including TypeScript checking and a production Vite build. No Chrome/Firefox walkthrough or UI test run has been recorded for that change.

Exact results and limitations are in the [audit follow-ups](audit/project-audit-2026-09-29.md). Migration and legacy-data procedures are in [operations](operations.md).

## Remaining before the exercise audit

1. Make Jenkins/SonarQube PR checks mandatory, establish UI lint and dependency review, and demonstrate that failed checks block merging.
2. Publish versioned release images and make Ansible deploy those exact images with the secure TLS/Vault configuration. Record clean, repeat, and rollback deployment results.
3. Finish the remaining payment lifecycle/API decisions and verify Stripe and PayPal against sandboxes. Credentials and a trusted provider-customer-to-local-user mapping are not available yet, so provider ownership proof remains deferred.
4. Run and record deployed user-deletion/retry, authorization/TLS, database persistence, tracing, load/failover, and Chrome/Firefox dashboard scenarios. The [browser checklist](audit/browser-checks.md) remains pending.
5. Reconcile checked phase claims and older architecture/API documentation with the implementation and dated evidence. Phase 7 payment completion and final audit readiness are not established.

Optional documentation, expanded test-suite, and Kubernetes bonuses in `plan.md` are separate from the mandatory gaps above.
