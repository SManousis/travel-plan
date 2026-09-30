# Development and CI

Run `./mvnw -B -ntp verify` for the backend, `npm ci && npm run lint:api` for API contracts, and `npm --prefix admin-ui ci && npm --prefix admin-ui run lint && npm --prefix admin-ui run build` for the dashboard. On Windows, use `mvnw.cmd`.

`Jenkinsfile` runs all three check groups for every branch it builds. On `main` it also runs SonarQube analysis, waits for the quality gate, publishes SHA-tagged images, and invokes Ansible. The stages fail when required tools, registry settings, credentials, or deployment inventory are missing. See [release deployment](deployment.md) for agent and target setup.

```sh
docker compose -f infra/compose/compose.ci.yml up -d
```

The local CI stack binds Jenkins and SonarQube to loopback and keeps data in named volumes. It supplies the services, not a configured Jenkins agent, plugins, credentials, or a demonstrated quality gate. SonarQube Community Build supports analysis of the main branch only. Protect `main` in Gitea with the PR validation job and record a failing-check rejection before marking Phase 3 complete.
