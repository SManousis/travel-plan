# Development and CI

Run `./mvnw -B -ntp verify` for the backend and `cd admin-ui && npm ci && npm run build` for the dashboard. Jenkins is defined in `Jenkinsfile` and runs backend tests, UI validation, optional SonarQube analysis, and the quality gate.

```sh
docker compose -f infra/compose/compose.ci.yml up -d
```

The CI stack binds only to loopback and keeps data in named volumes. Configure the Jenkins SonarQube installation name as `travel-plan-sonarqube`; inject repository and Sonar credentials through Jenkins credentials bindings.
