# Deployment roles

The deployment is intentionally composed around idempotent role boundaries. The host, Docker, storage, certificates, Vault, databases, application services, proxy, CI, and observability responsibilities map to the role names below; site-specific environments may split these task groups into separate `tasks/main.yml` files without changing the deploy contract.

`deploy.yml` owns release reconciliation and readiness. Secrets come from Ansible Vault or environment lookups and are never printed.
