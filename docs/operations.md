# Operations

`infra/ansible/site.yml` deploys the Compose environment repeatably. Credentials are supplied through Ansible Vault or an external secret manager. The scale override demonstrates two instances of each application service:

```sh
docker compose -f infra/compose/compose.yml -f infra/compose/compose.scale.yml up -d --scale api-gateway=2
```

Application health checks and private management ports are used for readiness. Database replication and failover remain separate concerns because named volumes do not provide database high availability.

Run `scripts/load/replica-smoke.sh` with `compose.scale.yml` to record status/error counts during a 40-request authenticated read sample. Stop one replica and repeat; Docker DNS routes new connections to survivors. Stop all instances of one service to verify a controlled dependency failure, then restore it with Compose. Application services use bounded HTTP client timeouts and idempotency keys for retried writes; this local demonstration does not claim multi-host or database high availability.
