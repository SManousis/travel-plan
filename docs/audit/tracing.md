# Tracing and operational visibility

The gateway creates or accepts `X-Request-ID`, returns it to the caller, and forwards it to downstream services. Each request emits a JSON log with timestamp, service, severity, request/trace ID, operation, and HTTP status. Passwords, bearer tokens, payment references, and request bodies are never logged.

The OTLP collector in `infra/compose/otel-collector.yml` accepts OTLP gRPC/HTTP on the private backend network and exports debug records for local verification. The observability overlay publishes no ports; production deployments must place the collector and its viewer behind authenticated TLS access. Health and metrics remain on each service's private management listener.

Verification: send a request with `X-Request-ID: audit-trace-1`, inspect gateway and service logs for `audit-trace-1`, then send an invalid downstream request and confirm the same ID appears with the controlled error status.

## Recorded local check — 2026-10-01

An authenticated user read with `X-Request-ID: audit-final-valid-20261001` returned 200. The same ID appeared in the gateway, auth-service token-validation, and user-service JSON logs. An invalid bearer token returned 401 with its supplied request ID in the response and gateway log. None of the inspected trace lines contained a bearer token, access token, or password. Commands, isolated Compose conditions, and remaining limits are in [the runtime follow-up](runtime-verification-2026-10-01.md). A collector/OTLP export and deployed trace search were not checked.
