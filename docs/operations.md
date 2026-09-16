# Operations

`infra/ansible/site.yml` deploys the Compose environment repeatably. Credentials are supplied through Ansible Vault or an external secret manager. The scale override demonstrates two instances of each application service:

```sh
docker compose -f infra/compose/compose.yml -f infra/compose/compose.scale.yml up -d --scale api-gateway=2
```

Application health checks and private management ports are used for readiness. Database replication and failover remain separate concerns because named volumes do not provide database high availability.
