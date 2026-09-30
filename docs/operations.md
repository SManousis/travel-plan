# Operations

`infra/ansible/site.yml` deploys the Compose environment repeatably. Credentials are supplied through Ansible Vault or an external secret manager. The scale override demonstrates two instances of each application service:

```sh
docker compose -f infra/compose/compose.yml -f infra/compose/compose.scale.yml up -d --scale api-gateway=2
```

Application health checks and private management ports are used for readiness. Database replication and failover remain separate concerns because named volumes do not provide database high availability.

## Upgrading an existing installation to persisted auth identities

Auth migration V3 creates `auth_identities`. Older provisioned passwords existed only in auth-service memory, so their hashes cannot be migrated. An installation with existing rows in the users database requires an explicit credential reset before normal traffic resumes.

1. Back up the auth and users databases, and put the gateway in maintenance mode so user writes and logins do not race the upgrade.
2. Start auth-service with the `infra` profile and confirm Flyway reached V3. Keep the bootstrap administrator credential available through the configured secret source.
3. Export `id`, `email`, `role`, and `status` from the users database. For **every** row, generate a unique strong replacement password through the approved secret-management process. Call the private `POST /api/auth/internal/provision` endpoint with `{ "userId": "<id>", "username": "<email>", "password": "<replacement>", "role": "<role>" }` and the `X-Internal-Service-Token` header. Do not place passwords in source control, shell history, or application logs.
4. For each user whose status is `DISABLED`, call the private `POST /api/auth/internal/identities/<id>` endpoint with `{ "username": "<email>", "role": "<role>", "enabled": false }` and the same internal credential. Confirm every users-database UUID has one matching auth identity with the expected role and enabled state. Resolve any `409` or `404` before opening traffic.
5. Deliver replacement passwords for active users through a secure channel. Restart auth-service and verify representative ADMIN and OPERATOR logins, disabled-user rejection, and direct-service token revocation. Then reopen the gateway.

This is a required one-time reset for upgrades from the memory-only auth implementation. There is currently no end-user password-change flow; subsequent password replacement remains an operator procedure until that feature is built.

Run `scripts/load/replica-smoke.sh` with `compose.scale.yml` to record status/error counts during a 40-request authenticated read sample. Stop one replica and repeat; Docker DNS routes new connections to survivors. Stop all instances of one service to verify a controlled dependency failure, then restore it with Compose. Application services use bounded HTTP client timeouts and idempotency keys for retried writes; this local demonstration does not claim multi-host or database high availability.
