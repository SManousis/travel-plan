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

## Upgrading legacy travel graph records

The earlier travel-service kept stops, activities, accommodation, transport, and participants only in process memory. Its Neo4j writes contained destination and top-level travel nodes. A restart lost the itinerary fields, so the new graph repository cannot infer them from the database.

1. Put travel writes in maintenance mode and back up Neo4j before deploying the new service. Record travel IDs and external references that may need reconciliation.
2. Inspect incomplete roots with `MATCH (t:Travel) WHERE NOT EXISTS { MATCH (t)-[:HAS_STOP]->(:DestinationStop) } RETURN t.id, t.name, t.startDate, t.endDate`. Restore each itinerary from an external source of record. While any incomplete root still has the `Travel` label, both its detail request and the whole travel-list request return `409 INCOMPLETE_LEGACY_TRAVEL`; do not reopen travel reads until every such root is reconciled or quarantined.
3. Before the new service initializes its normalized destination constraint, inspect duplicates with `MATCH (d:Destination) WITH toLower(trim(d.name)) AS name, toLower(trim(d.country)) AS country, collect(d.id) AS ids WHERE size(ids) > 1 RETURN name, country, ids`. Reconcile duplicate IDs and any external references before removing redundant nodes. Startup intentionally fails while duplicates violate the constraint.
4. For an incomplete root that can be replaced, record its old UUID, remove that specific root after backup with `MATCH (t:Travel {id: $id}) WHERE NOT EXISTS { MATCH (t)-[:HAS_STOP]->(:DestinationStop) } DETACH DELETE t`, then create the complete travel through the admin API. Record the old-to-new UUID mapping and reconcile any consumers of the old ID. If its itinerary cannot yet be reconstructed, preserve the root for operator review outside the live collection with `MATCH (t:Travel {id: $id}) WHERE NOT EXISTS { MATCH (t)-[:HAS_STOP]->(:DestinationStop) } REMOVE t:Travel SET t:LegacyTravelNeedsReview`. Track quarantined records with `MATCH (t:LegacyTravelNeedsReview) RETURN t.id, t.name, t.startDate, t.endDate`. A quarantined ID returns `404` from the travel API until it is restored as a complete travel.
5. Confirm the incomplete-root query in step 2 returns no rows before reopening reads. Verify complete aggregates with `MATCH (t:Travel)-[:HAS_STOP]->(s:DestinationStop)-[:AT]->(d:Destination) RETURN t.id, count(s) AS stops, collect(d.id) AS destinations ORDER BY t.id`. Check a representative itinerary and the travel list from a second travel-service instance before reopening writes.

Do not treat a top-level-only node as a completed itinerary. The original in-memory component data cannot be recovered from Neo4j alone.

Run `scripts/load/replica-smoke.sh` with `compose.scale.yml` to record status/error counts during a 40-request authenticated read sample. Stop one replica and repeat; Docker DNS routes new connections to survivors. Stop all instances of one service to verify a controlled dependency failure, then restore it with Compose. Application services use bounded HTTP client timeouts and idempotency keys for retried writes; this local demonstration does not claim multi-host or database high availability.
