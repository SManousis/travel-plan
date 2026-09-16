# Tracing and operational visibility

The gateway creates or accepts `X-Request-ID`, returns it to the caller, and forwards it to downstream services. Each request emits a JSON log with timestamp, service, severity, request/trace ID, operation, and HTTP status. Passwords, bearer tokens, payment references, and request bodies are never logged.

The OTLP collector in `infra/compose/otel-collector.yml` accepts OTLP gRPC/HTTP on the private backend network and exports debug records for local verification. The observability overlay publishes no ports; production deployments must place the collector and its viewer behind authenticated TLS access. Health and metrics remain on each service's private management listener.

Verification: send a request with `X-Request-ID: audit-trace-1`, inspect gateway and service logs for `audit-trace-1`, then send an invalid downstream request and confirm the same ID appears with the controlled error status.
