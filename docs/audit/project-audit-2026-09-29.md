# Project audit — 2026-09-29

## Summary

**Status: not ready to claim the mandatory project scope is complete.** The repository has a useful microservice skeleton, API contracts, infrastructure configuration, and some focused backend tests. However, multiple production-critical requirements are absent or are contradicted by the implementation. The roadmap marks Phases 7, 8, and 10–12 complete even though source and evidence do not support those claims.

This audit began as a **static repository audit** of commit `e01053e` (`main`) and now includes the checks recorded below. I inspected source, tests, configuration, scripts, and documentation. I did not start a container deployment or run browser checks, provider sandbox calls, live security/trace/failover rehearsals, or Ansible. Findings distinguish source-level facts from runtime evidence that still needs collection.

## Remediation follow-up — 2026-09-30

The initial findings below describe the 2026-09-29 baseline. On branch `build-security-data-fixes`, the first remediation package closed the **backend build blocker** and the **access-token revocation gap at API boundaries**:

- The PowerShell Maven Wrapper starts correctly with a normal `.m2` directory. The duplicate user-service `security` mapping and its startup dependency injection failure were corrected.
- Auth now persists issued/revoked sessions through `admin_sessions` in the `infra` profile. A protected `/internal/token-validation` endpoint checks current session state. Gateway, user, travel, and payment services call it after local token checks; revoked tokens receive `401` and validation outages receive `503`.
- Provisioned identity records now carry the user UUID, password hash, role, and enabled state in `auth_identities`. User deletion disables the identity and revokes its sessions; user email, role, or status updates synchronize the identity and revoke old sessions. Login and identity changes coordinate through an auth database transaction, and token validation also checks the identity's current role/enabled state. The default profile retains an in-memory fallback for local/test runs.

| Verification on 2026-09-30 | Result |
|---|---|
| `mvnw.cmd -B -ntp verify` | **Pass:** all six reactor modules succeeded; 57 tests, zero failures/errors/skips. |
| Focused auth, gateway, and direct-service authorization tests | **Pass:** active, revoked, and auth-outage decisions; UUID/role provisioning, identity updates, subject revocation, and JDBC cross-instance session/identity cases. |
| Auth Flyway chain against PostgreSQL-mode H2 | **Pass:** migrations V1–V3 applied, including the new identity table. H2 test setup defines a compatible `TIMESTAMPTZ` domain for the existing V1 migration. |
| Auth infra transaction wiring and rollback | **Pass:** the infra profile supplies JDBC transaction management; a failed identity/session operation rolls back the identity update. |
| Base, secure, and scale `docker compose ... config --quiet` | **Pass:** each command exited 0. Docker warned that this host denied access to its user `config.json`. |

This is code and local-test evidence, not a deployed PostgreSQL or multi-replica rehearsal. Existing users require the credential reset and identity reconciliation procedure in `docs/operations.md` because their prior auth password hashes were never persisted. The 2026-09-29 baseline tables below are retained for traceability. **Still open:** process-local failed-login throttling, durable coordination between auth and user database updates, an end-user password-change flow, and the payment, dashboard, CI/CD, deployment, and final-rehearsal findings. The travel finding has a separate follow-up below. The project remains **not ready** to claim the mandatory scope complete.

## Travel persistence follow-up — 2026-09-30

On the same branch, travel-service now uses Neo4j as its source of truth whenever the `infra` profile provides a driver. Destination CRUD, complete travel itineraries, and participation are stored and read through graph transactions. Travel deletion removes owned itinerary and participation nodes while preserving shared destinations. The default profile retains its in-memory store for local runs.

| Verification on 2026-09-30 | Result |
|---|---|
| `mvnw.cmd -B -ntp verify` | **Pass:** all six reactor modules succeeded; 61 tests, zero failures/errors/skips. |
| In-process Neo4j lifecycle tests | **Pass:** four tests cover complete itinerary reads across store instances, updates and response ordering, participant idempotence and current-state update responses, destination protection, owned-node cleanup, write rollback, and explicit rejection of top-level-only legacy travel nodes. |
| Base, secure, and scale `docker compose ... config --quiet` | **Pass:** all three exited 0; Docker warned that this host denied access to its user `config.json`. |

This closes the specific **process-local travel aggregate and partial graph persistence** finding in the `infra` profile at code and in-process graph-test level. It does not establish deployed Neo4j/replica behavior: Docker's engine was unavailable on this host. Existing top-level-only travel nodes lack the lost in-memory itinerary data and require the operator procedure in `docs/operations.md`. An unreconciled root makes both its detail request and the travel collection return `409 INCOMPLETE_LEGACY_TRAVEL`; the runbook requires all such roots to be reconstructed or explicitly quarantined before serving reads. The travel OpenAPI/domain mismatch, cross-service traveler deletion cleanup, and Phase 11 recovery/load evidence remain open. Baseline findings below retain their original wording for comparison.

## Payment persistence follow-up — 2026-09-30

On `build-security-data-fixes`, payment-service now has a PostgreSQL-backed store in the `infra` profile for gateways and payment methods. Migration V2 adds gateway relationships, lifecycle fields, and uniqueness constraints. New methods are saved as `PENDING` before provider inspection; Stripe and PayPal adapters now make sandbox HTTP requests to inspect supplied references and to detach/delete them. A method becomes `ACTIVE` only after a successful provider response. Failed provider work remains visible with a safe failure code, and payment APIs return `503` for unavailable storage/provider dependencies. The default profile keeps an in-memory metadata store, with the same provider verification requirement.

This is **code and compile evidence only**. No Stripe or PayPal credentials were available, so neither provider flow has been demonstrated against a live sandbox. Existing V1 payment rows require the reconciliation procedure in `docs/operations.md` before payment-list reads resume. The current endpoints remain synchronous and do not yet implement the operation resources, default selection, Vault secret resolution, provider-customer-to-local-user ownership proof, user-deletion cleanup, or webhook decisions described in `docs/api/payments.yaml` and the unchecked Phase 7 tasks. The original 2026-09-29 findings below are retained as baseline evidence; Phase 7 is still **not complete**.

## Verification run — 2026-09-29

| Check | Result | Notes |
|---|---|---|
| API contracts: `npm ci && npm run lint:api` | **Pass** | All four OpenAPI documents passed Redocly validation. The host ran Node 24.18.0/npm 11.16.0, while `package.json` requires Node `>=22.12.0 <23`; this is outside the declared support range. |
| Dashboard: `npm --prefix admin-ui ci && npm --prefix admin-ui run build` | **Pass** | TypeScript checking and the Vite production build completed successfully. The same Node 24 runtime mismatch applies. |
| Base Compose: `docker compose -f infra/compose/compose.yml config` | **Pass** | Configuration resolved successfully. Docker warned that it could not read `C:\\Users\\User\\.docker\\config.json` because access was denied. |
| Secure Compose overlay: `docker compose -f infra/compose/compose.yml -f infra/compose/compose.secure.yml config --quiet` | **Pass** | Configuration resolved successfully, with the same Docker user-config warning. |
| Scale Compose overlay: `docker compose -f infra/compose/compose.yml -f infra/compose/compose.scale.yml config --quiet` | **Pass** | Configuration resolved successfully, with the same Docker user-config warning. |
| Backend: `mvnw.cmd -B -ntp verify` | **Fail** | Maven Wrapper 3.3.4 crashes in its PowerShell launcher because it indexes the null `DirectoryInfo.Target` on `C:\\Users\\User\\.m2`. Running the downloaded Maven 3.9.16 directly confirmed the actual project failure: `user-service/src/main/resources/application.yml` contains duplicate top-level `security:` keys, so Spring rejects the configuration before `user-service` tests start. Maven completed auth-service (6 passing tests) then stopped; travel, payment, and gateway modules were skipped. |
| Ansible: `ansible-playbook --syntax-check infra/ansible/deploy.yml` | **Not run** | `ansible-playbook` is not installed on this host. |

The first actionable build blocker is the duplicate `security:` key in `user-service/src/main/resources/application.yml`. The wrapper error is a separate Windows/PowerShell launcher defect that prevents the documented `mvnw.cmd` command from starting Maven on this host.

## Highest-priority findings

| Severity | Finding | Evidence and impact |
|---|---|---|
| **Critical** | **JWT revocation is not enforced at the gateway or business-service boundaries.** | Auth keeps revoked token IDs and subjects in process memory in `auth-service/src/main/java/com/travelplan/auth/security/TokenService.java`. Gateway and service filters independently validate token signatures/claims and do not consult that state. Logging out or deleting a user therefore does not reliably invalidate an already-issued token for business API requests. Revocation state is also lost on restart and is not shared across auth replicas. |
| **Critical** | **Payment APIs are in-memory and provider adapters do not contact Stripe or PayPal.** | `PaymentController` stores gateways and methods in `ConcurrentHashMap`; the provider adapters only reject blank references and echo any other string as `ACTIVE`. The payment migration exists, but the controller never writes to PostgreSQL. Restarting loses records; replicas can show different data; arbitrary references can be reported active without provider verification. No sandbox exchange is implemented. |
| **Critical** | **The travel graph does not persist or read complete itineraries.** | `TravelStore` serves lists/details from process-local maps. Neo4j writes only a destination node or the top-level travel node; stops, activities, accommodations, transport, participants, and updates are not durably modeled/read. A restart or another replica will not have the full records, contradicting the Phase 6 and Phase 11 completion claims. |
| **High** | **The dashboard does not provide the required admin workflows.** | `admin-ui/src/App.tsx` implements login, summary cards, and a user-create form. There are no traveler, travel, payment-method, or gateway CRUD screens; no user list/detail/edit/delete flow; and no refresh/logout API flow. The UI test checklist in `plan.md` is also still unchecked. |
| **High** | **Ansible/Jenkins deployment is not wired to deploy a built release.** | Jenkins has no image/artifact publication stage and passes `BUILD_TAG` as `release_tag`. `deploy.yml` does not use that value to select images; it copies Compose manifests and runs them, while `compose.yml` uses local `build:` contexts. The playbook copies only the Compose directory, so its relative build contexts do not have the application source at the target. The `site.yml` does not provide the roles claimed by the plan. |
| **High** | **Security/TLS/Vault settings are not in the deployment path.** | Jenkins/Ansible deploy only `infra/compose/compose.yml`, not the secure overlay. The base Compose file supplies development fallback credentials and placeholder token/admin secrets. Vault is configured/documented, but there is no evidence of app-side secret retrieval or deployment-time injection from Vault. Thus the checked Phase 4 security completion statement is not demonstrated for the deployed stack. |
| **High** | **Audit completion is asserted without recorded audit results.** | `docs/audit/browser-checks.md`, `resilience.md`, `security-rehearsal.md`, and `tracing.md` are procedures requesting results to be recorded; they contain no dated browser, failover, TLS, or trace run results. `evidence.md` instructs the reader to run a script rather than preserving its output. The Phase 12 checkbox and checklist imply completion, but current repository evidence does not establish it. |

## What is in good shape

- The Maven reactor separates auth, users, travel, payment, and gateway modules; a Maven Wrapper and separate frontend lockfile are present.
- OpenAPI contracts, architecture/data-model notes, API error conventions, and version documentation provide a useful design baseline.
- Compose defines separate PostgreSQL databases/users, Neo4j, private service networking, named volumes, and loopback-published local entry points. Migration and graph initialization files exist.
- Backend security filters, admin bootstrap, password hashing, token signing, management-port isolation, and service-level route checks have been implemented as a foundation. Their revocation and deployment limitations are listed above.
- There are focused gateway routing/startup tests, token-service tests, and travel-store tests. This is useful coverage for the implemented portions, but it is not evidence for all business, provider, persistence, UI, or distributed lifecycle behavior.
- The README clearly identifies prerequisites and local commands. The audit and operations docs explicitly acknowledge database high-availability limits.

## Other missing or incomplete requirements

1. **User persistence and lifecycle reliability:** `UserStore` does use JDBC in the infrastructure profile, but it also relies on a local cache, silently ignores all startup load failures, and coordinates identity/profile writes without a durable operation/outbox. Deletion only disables the user and deletes travelers in the current store; cross-service travel/payment cleanup is not wired. Verify transactional ordering, concurrency, retry, and replica behavior before relying on the Phase 5 completion claim.
2. **Auth persistence and rate limiting:** provisioned credentials and revocation state are in-memory; failed-login state is local. A restart/replica can lose this state. This conflicts with the architecture requirement for shared identity/session state.
3. **Payment requirements still explicitly unchecked:** local-vs-provider-controlled edit policy, timeouts/durable retry states, webhook signature/deduplication (if required), and failure-visible user cleanup remain unchecked in `plan.md`.
4. **CI quality enforcement is incomplete:** Sonar and its gate are conditional on `SONAR_HOST_URL`; UI lint is invoked with `--if-present` but no lint script exists; there are no UI unit tests. Per-PR Sonar support, dependency/vulnerability review, and proof that bad changes block a PR remain unchecked.
5. **Scale/recovery claims exceed source evidence:** process-local auth, travel, and payment state cannot be consistent across replicas. `scripts/load/replica-smoke.sh` sends unauthenticated requests by default and only counts HTTP codes; it does not authenticate, measure latency, record instance IDs, or perform failure/recovery assertions as the docs/plan require.
6. **Documentation is stale and conflicts with implementation:** `docs/architecture.md` still says business APIs, persistence, authentication, and integrations are future work; `docs/data-model.md` calls schemas planned, while the roadmap/evidence calls phases complete. The two views need reconciliation.
7. **Operational security and deployment validation:** the secure Compose overlay depends on provisioned TLS files and is not selected by Ansible. The default inventory is a local host. No checked-in run result demonstrates a clean/repeat deployment, TLS verification, database backup/restore, or a live Vault policy/credential flow.

## Roadmap/status inconsistencies

The roadmap itself leaves Phase 3 incomplete and retains unchecked requirements in Phases 7 and 8. Phase 3 is explicitly not complete, and the mandatory CI/dependency review items remain open. Phases 7, 8, 10, 11, and 12 are marked complete in the overview, but source review found the blocking gaps above. Bonuses A–C are clearly optional and unchecked; they are not blockers if the mandatory scope is the target.

The audit documents are mostly **rehearsal instructions**, not recorded evidence. A static audit cannot establish that Compose, migrations, TLS, Ansible, browsers, provider sandboxes, or failover work at runtime. The report should be treated as a source-based gap assessment until those runs are performed and their outputs/results are recorded.

## Crosswalk: unchecked checklist items vs. audit findings

### Gaps already acknowledged by the checklist

| Phase | Unchecked requirement | Audit result |
|---|---|---|
| 3 — CI/PR workflow | Per-PR SonarQube support; reviewed-main deployment; dependency/vulnerability process; proof that failing tests/quality gates block PRs | **Confirmed missing.** Sonar analysis and the quality gate are optional when `SONAR_HOST_URL` is unset, no per-PR mode is documented, and the pipeline has no release publication step. The documented deployment does not deploy an immutable built artifact. |
| 7 — Payments | Local-only edits; provider timeouts/idempotency/durable retry; in-use gateway and user-cleanup lifecycle; verified webhook handling where needed | **Confirmed missing.** These requirements are absent. The audit also found a larger prerequisite gap: payment records are in-memory and the Stripe/PayPal adapters never call either provider, even though the roadmap marks provider flows and CRUD complete. |
| 8 — Dashboard | Unit tests for protection, validation, errors, and CRUD state changes | **Confirmed missing.** No UI tests exist. The audit also found that the required CRUD screens themselves are largely absent, which is more fundamental than the unchecked test item. |
| Bonus A | Expanded docs and worked data-model/backup examples | **Confirmed incomplete.** Current architecture and data-model documents still describe future/planned behavior and conflict with checked phases. This is optional. |
| Bonus B | Database integration, multi-service lifecycle, browser E2E, provider test suites | **Confirmed incomplete.** No such test suites are present. This is optional, but it would expose several mandatory-runtime gaps. |
| Bonus C | Kubernetes deployment with Ansible | **Not implemented.** This is optional. |

### Gaps hidden by checked checklist items

| Marked complete phase | Audit finding that should reopen it | Why it exceeds an unchecked item |
|---|---|---|
| 1 — Foundation | The documented Maven Wrapper cannot run on this PowerShell host; backend verification also fails because `user-service` has duplicate `security:` YAML keys. | The phase completion check requires `mvnw verify`; the documented command currently fails before project verification can complete. |
| 4 — Authentication/security | Revocation, provisioned identity data, and failed-login state are process-local. Gateway/business services do not enforce auth-service revocation. The secure/Vault configuration is not selected by deployment. | These are core completion requirements, including revocation, direct-service protection, TLS, managed secrets, and replica-safe access control. |
| 5 — Users/travelers | Cross-service cleanup is not wired; durable deletion operations, retry states, and outbox-style coordination are absent. | The checked lifecycle claim includes end-to-end deletion/retry behavior, beyond the current local disable/remove behavior. |
| 6 — Travel/itineraries | Complete aggregates are held in memory; Neo4j persists only portions of travel/destination state and does not reload full data. | The checked phase claims durable graph CRUD, transactions, and cleanup. |
| 7 — Payments | In-memory storage and simulated provider adapters invalidate the checked CRUD and sandbox-flow claims. | These are prerequisites for the four already-unchecked payment hardening items. |
| 8 — Dashboard | Only login, summary cards, and user creation are implemented; user update/delete and traveler/travel/payment/gateway CRUD workflows are missing. | The checked completion criteria requires all required admin workflows in the production build. |
| 9 — Observability | Procedures exist, but no recorded successful trace/redaction run establishes end-to-end behavior. | The phase’s completion check requires finding both gateway and service logs for propagated request IDs. |
| 10 — Deployment | Jenkins does not publish images/artifacts and Ansible deploys a Compose file with local `build:` contexts that it does not copy to the target. Ansible itself was unavailable for local syntax validation. | The checked criteria requires repeatable deployment of immutable artifacts, rollout/rollback evidence, and validated playbooks. |
| 11 — Replicas/recovery | Stateful auth, travel, and payment components are process-local; the load script is unauthenticated and does not measure/rehearse the stated recovery criteria. | The phase claims shared state, healthy routing, failover, measured load, and recovery evidence. |
| 12 — Final rehearsal | Security, browser, trace, deployment, and resilience documents are instructions with no recorded results. | The phase completion check requires reproducible, recorded evidence with no unresolved blockers. |

**Conclusion:** The unchecked list correctly exposes CI, payment hardening, UI tests, and optional work. It does not capture the most serious implementation gaps. Reopen Phases 1 and 4–12, retain Phase 3 as incomplete, then split the hidden findings into explicit unchecked tasks before treating the roadmap as a delivery tracker.

## Recommended order to close gaps

1. Fix shared authentication/revocation semantics first and verify logout, user deletion, restart, and multi-replica behavior.
2. Implement durable travel and payment repositories/transactions; add provider sandbox calls, timeout/idempotency handling, and durable failure states.
3. Complete dashboard workflows for users, travelers, travels/itineraries, and payments, then add UI tests.
4. Make CI checks mandatory for PRs, publish immutable artifacts/images, and make Ansible deploy those exact artifacts using the secure configuration and injected secrets.
5. Reconcile roadmap/docs with actual code; run the final security, persistence, browser, deployment, trace, and replica rehearsals and preserve dated results.

