# Deployment automation

`infra/ansible/site.yml` bootstraps the application directory and Compose manifests. `infra/ansible/deploy.yml` performs an immutable-tag reconciliation, renders secrets with `no_log` and mode `0600`, waits for gateway readiness, and is safe to run repeatedly. Jenkins invokes it only after tests and the quality gate on `main`; set `TRAVEL_PLAN_TAG` to the published image tag. Deployments are rolling at the Compose service level, while Flyway migrations must remain backward compatible during a release. Roll back by selecting the previous immutable tag and rerunning the playbook.

Validate with `ansible-playbook --syntax-check infra/ansible/deploy.yml` and `ansible-lint infra/ansible/deploy.yml` where installed. A second run should report no manifest changes and leave named database volumes intact.
