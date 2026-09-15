# Security implementation

The gateway and each business service require a signed administrator token for every `/api/**` business request. Login and refresh are the authentication exceptions; health endpoints remain available only on the private management bind address. Missing or invalid tokens return `401`, while a valid non-`ADMIN` token returns `403`.

The auth service bootstraps one administrator from `ADMIN_USERNAME` and `ADMIN_PASSWORD`. Passwords are BCrypt hashed at startup. Access tokens are short lived, signed with the shared `AUTH_TOKEN_SECRET`, and contain a subject, role, expiry, and unique identifier. Refresh rotates the token and revokes the previous identifier; logout also revokes the presented token. The secret must contain at least 32 characters and is supplied through the environment or Vault in deployed environments.

For local TLS, generate a trusted development CA with `infra/tls/generate-dev-ca.sh` and configure the browser proxy certificate paths from the generated files. Production deployments must terminate HTTPS at the entry proxy and use certificate verification for service, database, Vault, and monitoring connections. Vault policy templates under `infra/vault/policies/` define separate read scopes for each service; inject these secrets at deployment time rather than committing credentials.
