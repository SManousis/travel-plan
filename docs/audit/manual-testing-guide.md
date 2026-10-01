# Manual testing guide

Use this guide on `build-security-data-fixes` before asking for a merge. It covers the checks that still need a person, a browser, configured CI, a release host, or real provider sandbox accounts. The [dated implementation record](essentials-2026-10-01.md) contains checks already run locally; repeat them on the final commit before claiming audit completion.

**Result rule:** Mark each check **PASS**, **FAIL**, or **BLOCKED**. PASS needs an observed result and evidence. BLOCKED means the required environment or credential is missing; it is not a pass. Work only with disposable test users and payment references. Never put passwords, bearer tokens, provider references, private keys, or unredacted environment files in screenshots, tickets, or this repository.

## 0. Prepare and record the run

Run commands from the repository root. Use PowerShell 7 for the local examples and a Linux Jenkins/Ansible agent for deployment commands.

```powershell
git branch --show-current
git rev-parse HEAD
git status --short
```

Record the branch, full 40-character SHA, date/time, tester, Docker/Jenkins/Sonar versions, browser versions, and environment in a copy of the results table at the end. Start with a clean working tree. Keep the current branch unmerged until the review is approved.

| Environment | Use it for | Required setup |
| --- | --- | --- |
| Local synthetic stack | Gateway, CRUD, dashboard, database persistence, request logs, and payment flow rehearsal | Docker, Node.js 22, the ignored `.env.payment-sim` file |
| CI and staging | Gitea PR gate, Jenkins, SonarQube, TLS, Ansible release and rollback | Configured Linux CI agent, protected credentials, image registry, reviewed staging inventory and certificates |
| Real provider sandbox | Stripe and PayPal reference/ownership proof | Provider-issued sandbox credentials and a trusted local-user-to-provider-customer mapping |

The local payment simulator uses fake credentials and **does not** prove Stripe or PayPal integration. Do not use the checked-in `infra/ansible/inventory.ini` as a production target; it points to localhost. See [deployment setup](../deployment.md) and [payment limits](../payments.md).

## 1. Start the local synthetic stack

If `.env.payment-sim` already exists, keep it; the generator refuses to overwrite it. If it does not exist, generate it:

```powershell
if (-not (Test-Path -LiteralPath '.env.payment-sim')) { node scripts/sandbox/create-sim-env.mjs }
docker compose --env-file .env.payment-sim -p travel-plan-payment-sim -f infra/compose/compose.yml -f infra/compose/compose.payment-sim.yml up -d --build --wait admin-ui
docker compose --env-file .env.payment-sim -p travel-plan-payment-sim -f infra/compose/compose.yml -f infra/compose/compose.payment-sim.yml ps
```

**Expected:** PostgreSQL, Neo4j, the simulator, four services, and gateway are healthy; the dashboard is running and returns HTTP `200`. Open `http://127.0.0.1:15173`. API is `http://127.0.0.1:18080`. Sign in as `sim-admin` using `ADMIN_PASSWORD` from your local ignored `.env.payment-sim` file; do not paste that value into evidence.

The two fake references are `pm_simulatedvisa001` (Stripe-shaped) and `VAULT_SIM_001` (PayPal-shaped). They are fixed fixtures. If the demo already has retained methods using them, inspect those records first. To reuse the references, delete the retained methods through the UI/API and restart `payment-simulator`; do not delete database volumes merely to reset a fixture. The optional `scripts/sandbox/smoke-payment-sim.ps1` creates and deletes methods automatically, but run it only when both references are available. See [simulator instructions](../payments.md#local-simulation-without-provider-accounts).

## 2. Gateway and authentication

Start with denial checks. In PowerShell, `curl.exe` avoids the `curl` alias:

```powershell
curl.exe -sS -o NUL -w "%{http_code}`n" http://127.0.0.1:18080/api/users
curl.exe -sS -o NUL -w "%{http_code}`n" -H "Authorization: Bearer invalid" http://127.0.0.1:18080/api/users
```

**Expected:** `401` for both. Then sign in with the dashboard. A valid admin can open users, travels, and payments; a wrong password cannot. Sign out and confirm the old session can no longer use protected pages. If you can issue an `OPERATOR` token through the approved operator credential process, confirm a business endpoint returns `403` for it. Do not mark the non-admin check PASS without that token.

For a direct API read and later log check, keep the token only in the current PowerShell process:

```powershell
$settings = @{}
foreach ($line in [IO.File]::ReadAllLines((Join-Path $PWD '.env.payment-sim'))) {
  $parts = $line.Split('=', 2)
  if ($parts.Length -eq 2) { $settings[$parts[0]] = $parts[1] }
}
$loginBody = @{ username = $settings.ADMIN_USERNAME; password = $settings.ADMIN_PASSWORD } | ConvertTo-Json -Compress
$login = Invoke-RestMethod -Uri 'http://127.0.0.1:18080/api/auth/login' -Method Post -ContentType 'application/json' -Body $loginBody
$traceId = 'manual-users-' + [guid]::NewGuid().ToString('N')
$headers = @{ Authorization = "Bearer $($login.accessToken)"; 'X-Request-ID' = $traceId }
$response = Invoke-WebRequest -Uri 'http://127.0.0.1:18080/api/users' -Headers $headers
$response.StatusCode
$response.Headers['X-Request-ID']
```

**Expected:** `200` and the same request ID. Keep `$headers` in memory for later checks; clear the terminal session afterward. On a TLS staging release, use the HTTPS hostname on the certificate and a trusted CA. Do not use `curl -k`/`--insecure` as TLS evidence. Confirm service and management ports are private and only intended entry ports are reachable. Use the [security checklist](security-rehearsal.md) for the logout/revocation sequence.

On staging, also review database roles, Vault policies, certificate validity/rotation, and whether protected values can be read only by their intended service. Record commands and redacted policy names. App-side Vault secret retrieval is not implemented yet, so mark that specific requirement BLOCKED rather than inferring it from the presence of Vault files.

## 3. Dashboard CRUD in Chrome and Firefox

Run the following at **1440 px, 768 px, and 390 px** viewport widths in both browsers. Use browser developer tools to set widths. Record the browser/version and a screenshot of each width with no horizontal clipping. Check keyboard Tab order, visible focus, labels, validation text, pending buttons, and readable list/cards. Use disposable records and note their UUIDs without personal data.

1. **Users/travelers:** Create an `OPERATOR` user; confirm it appears in the list and detail view. Edit its name and role/status, then refresh. Create a traveler for the active user, edit it, and confirm it remains after refresh. Try a duplicate email and missing/invalid fields: expect a visible controlled error with no duplicate. Do not delete the bootstrap admin.
2. **Destinations/travels:** Create two destinations, then a travel with ordered stops, dates, an activity, accommodation, transport, and the test traveler. Open detail and confirm the entire itinerary and participant persist. Edit the itinerary, remove/re-add the traveler, then refresh. Attempt to delete a destination still used by a travel: expect a conflict. **Leave this travel and participant in place** for the user-deletion check.
3. **Gateways/methods:** Create test Stripe and PayPal gateways. Connect each fake reference from section 1 to the disposable user. Confirm `ACTIVE`, safe display metadata, no raw provider reference in the response/list, exactly one default, **Make default** switching, label edit, and refresh persistence. While a connection returns `202`, inputs should stay locked; use browser Network tools to check that a retry sends the same `Idempotency-Key` without copying the bearer token into evidence. Attempt gateway deletion while a method is linked: expect `409`. Remove one method and its gateway to check normal cleanup; **leave the other method linked** for user deletion.
4. **Persistence checkpoint:** Before deleting the test records, record their UUIDs and restart the three data services:

   ```powershell
   docker compose --env-file .env.payment-sim -p travel-plan-payment-sim -f infra/compose/compose.yml -f infra/compose/compose.payment-sim.yml restart user-service travel-service payment-service
   docker compose --env-file .env.payment-sim -p travel-plan-payment-sim -f infra/compose/compose.yml -f infra/compose/compose.payment-sim.yml ps
   ```

   Wait for the services to become healthy, refresh the dashboard, and confirm the same user, method, travel UUIDs, itinerary, and participant remain. Do not run a destructive `down -v` on data you need to inspect.
5. **User deletion:** With the remaining method and traveler/travel participation recorded, delete the disposable user. `202` means cleanup is incomplete: inspect method status/failure and retry the same user deletion after resolving the dependency. Only record PASS at `204` after auth access is revoked, owned traveler/method data is gone, and the travel/destinations remain with the deleted traveler removed. Then delete the leftover gateway, travel, and disposable destinations. See [deletion recovery](../operations.md#resuming-user-deletion).

Check a deliberately invalid UUID and a missing ID through the API or UI; expect `400`/`404` without a crash.

The detailed [browser walkthrough](browser-checks.md) can be used as a page-by-page tick sheet. Full Chrome/Firefox proof requires actual results in both browsers; a Vite build or unit test is insufficient.

## 4. Request IDs, logs, and database state

Use `$traceId` from section 2 and inspect the participating services:

```powershell
docker compose --env-file .env.payment-sim -p travel-plan-payment-sim -f infra/compose/compose.yml -f infra/compose/compose.payment-sim.yml logs --no-color --since=10m api-gateway auth-service user-service | Select-String $traceId
```

**Expected:** gateway, auth validation, and user-service request lines share the `traceId`; each line has `timestamp`, `service`, `instanceId`, `severity`, `operation`, and `status`. The `instanceId` stays constant per process. Confirm no password, bearer token, provider credential/reference, or raw card data appears. Check an invalid token and a controlled backend error with separate IDs. The collector currently has no application export or searchable retained backend: mark OTLP ingestion/search **BLOCKED**, not PASS, until that is deployed and tested. See [tracing limits](tracing.md).

On staging, inspect Flyway history in each PostgreSQL database and a representative Neo4j travel aggregate using the database-owner access path. Compare stored IDs with the API records and check that a service restart does not erase them. Inspect schema constraints and indexes without exporting credential columns. If a user create fails between auth and users databases, follow the [reconciliation procedure](../operations.md#reconciling-failed-user-creation); this remains an implementation gap, not a passing test.

## 5. Jenkins, SonarQube, and Gitea PR gate

The CI stack from `infra/compose/compose.ci.yml` provides controllers only. Configure the `travel-plan-ci` Linux agent, plugins, Sonar server/webhook, registry credentials, and Gitea integration as described in [deployment setup](../deployment.md). Verify Gitea webhook delivery explicitly: pushes to this branch have succeeded while its remote hook printed a nonblocking `fatal: ambiguous argument` warning.

1. Create a disposable PR branch from a reviewed base. Push a deliberately failing dashboard or backend unit test on that branch. Confirm Jenkins runs backend, API, dashboard, npm advisory, and Ansible checks, publishes JUnit results, reports a failed status to Gitea, and Gitea prevents merge.
2. Revert the deliberate failure on that **same PR branch**. Confirm a new Jenkins run is green, test reports and build artifacts are retained, and the required status updates in Gitea. Keep the PR unmerged until review.
3. SonarQube Community Build is configured for **main analysis only**; do not expect native PR decoration. In a safe rehearsal project, demonstrate a failed quality gate and its recovery. Record Sonar analysis URL, gate condition, Jenkins build ID, and webhook result. Do not trigger the real `main` release job until registry and staging deployment targets are ready: that job publishes images and invokes Ansible.
4. Review both npm audit results, Java dependency advisories, and container image scan results for the exact release SHA. Record the affected package/image, severity, upgrade or mitigation, reviewer, and decision. The npm stage does not scan Java dependencies or images.

If a required job never starts or a failing job does not block merge, record FAIL. If the agent/credentials/branch protection are not configured, record BLOCKED. Source `Jenkinsfile` stages alone are not CI evidence.

## 6. Clean, repeat, and rollback staging deployment

Use a disposable Debian/Ubuntu staging host, reviewed inventory, real TLS material, protected secrets, registry access, and database backups. The checked-in localhost inventory is only an example. Run syntax/lint on the configured agent first. Supply secret values through Jenkins credential bindings or Ansible Vault, never `-e` command-line secret literals.

```sh
ansible-playbook --syntax-check -i "$DEPLOY_INVENTORY" infra/ansible/site.yml
ansible-lint infra/ansible/site.yml infra/ansible/prepare.yml infra/ansible/deploy.yml
ansible-playbook -i "$DEPLOY_INVENTORY" infra/ansible/site.yml \
  -e "release_tag=$RELEASE_TAG" -e "image_prefix=$IMAGE_PREFIX" -e "registry_host=$REGISTRY_HOST"
```

`RELEASE_TAG` must be the full reviewed commit SHA. **Expected clean run:** Ansible recap has zero failures; Docker/Compose is installed, six application images use that SHA, containers become healthy, HTTPS `/health` returns `200`, and unauthenticated `/api/users` returns `401`. Verify with the hostname and CA matching the certificate:

```sh
curl --cacert "$CA_FILE" -o /dev/null -w '%{http_code}\n' "https://$DEPLOY_HOST:8443/health"
curl --cacert "$CA_FILE" -o /dev/null -w '%{http_code}\n' "https://$DEPLOY_HOST:8443/api/users"
```

Do not print the rendered `.env` file or `docker compose config` into evidence because they can expose secrets.

Run `infra/ansible/deploy.yml` again with the **same** release variables. Record task changes and container IDs, and verify the test records created in section 3 still exist. For rollback, first confirm the previous SHA image set exists and its database migrations are compatible with current data. Back up the databases, deploy the previous SHA on staging with `deploy.yml`, repeat HTTPS/API/data checks, and record the result. A successful Compose update is not zero-downtime or database-HA proof. If no reviewed registry, TLS, or Linux host exists, mark these checks BLOCKED.

## 7. Real Stripe and PayPal sandbox verification

Only run this section after you have provider-issued sandbox credentials and a trusted mapping between each local user and the corresponding Stripe customer / PayPal vault customer. The fake simulator cannot supply that proof.

1. In the provider sandbox, complete Stripe SetupIntent attachment to the intended sandbox customer; for PayPal, complete the approved setup-token exchange to a vault payment token. Store credentials in the protected deployment secret source. Never enter PAN/CVV or provider secrets in this app or evidence.
2. Through the admin API/UI, connect each resulting opaque reference to the **mapped** local user. Confirm the provider dashboard and local `ACTIVE` record refer to the same customer and safe display metadata. Record redacted provider object IDs and method UUIDs.
3. Verify label/default edits, duplicate reference rejection, provider rejection/unavailability state, same-key retry, and deletion/detachment in the provider dashboard. Confirm a failed cleanup leaves a visible failed/pending record and does not claim success.

Without both providers' credentials and ownership mapping, mark this section BLOCKED. Do not mark it PASS based on `pm_simulatedvisa001` or `VAULT_SIM_001`.

## 8. Replica and failure checks

The current base Compose file publishes gateway port `8080` on the host. Scaling `api-gateway` to two replicas with that unchanged port causes a collision; the `compose.scale.yml` example alone does not demonstrate full Phase 11. Start with a **single business service** on the disposable local stack:

```powershell
docker compose --env-file .env.payment-sim -p travel-plan-payment-sim -f infra/compose/compose.yml -f infra/compose/compose.payment-sim.yml up -d --scale user-service=2 user-service
docker compose --env-file .env.payment-sim -p travel-plan-payment-sim -f infra/compose/compose.yml -f infra/compose/compose.payment-sim.yml ps user-service
```

Send repeated authenticated `GET /api/users` requests with unique `X-Request-ID` values. Inspect user-service logs for both `instanceId` values and record response codes/latency. Stop **one identified user-service container** with `docker stop <container-name>`, repeat the reads, then restore it with the Compose `up -d --scale user-service=2 user-service` command. Expected: healthy survivor serves requests and stored data remains. Also stop all instances of a disposable service, confirm affected operations fail clearly while unrelated areas work, then restore it. Do not use the current `scripts/load/replica-smoke.sh` as authenticated-load evidence: it sends no bearer token. Full gateway scaling, formal authenticated load targets, and database failover remain BLOCKED until their ingress/test setup is completed.

## 9. Close the run

Create a dated results file such as `docs/audit/manual-results-YYYY-MM-DD.md` and fill one row for every section. Link redacted screenshots, Jenkins/Gitea/Sonar URLs, Ansible recap, certificate probe output, request IDs, and database checks. Include failures and blocked items; do not check off a phase in `plan.md` solely because code exists.

| ID | Environment / SHA | Expected | Observed result | PASS / FAIL / BLOCKED | Evidence link or blocker |
| --- | --- | --- | --- | --- | --- |
| 1 Local stack | | Healthy services and dashboard | | | |
| 2 Auth/gateway | | 401/403/200 and revocation as applicable | | | |
| 3 Browser CRUD | | All workflows in Chrome and Firefox at three widths | | | |
| 4 Logs/data | | Correlated IDs, persisted records, no sensitive log values | | | |
| 5 PR/quality gate | | Failed checks block merge; corrected run passes | | | |
| 6 Release | | Clean, repeat, and compatible rollback pass | | | |
| 7 Providers | | Both real sandbox references and customer ownership verified | | | |
| 8 Replicas/failure | | Survivor traffic, recovery, and recorded limits | | | |

Before final audit sign-off, review [the phase checklist](../../plan.md), [security rehearsal](security-rehearsal.md), [browser checklist](browser-checks.md), and [operations recovery](../operations.md). Record unresolved product gaps as open work, especially durable cross-database user creation, provider ownership, collector export/viewing, mutation audit records, and full gateway scaling.
