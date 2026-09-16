#!/usr/bin/env bash
set -Eeuo pipefail

psql --username "$POSTGRES_USER" --dbname postgres \
  --set=auth_password="$AUTH_DB_PASSWORD" \
  --set=users_password="$USERS_DB_PASSWORD" \
  --set=payments_password="$PAYMENTS_DB_PASSWORD" <<'SQL'
DO $$ BEGIN
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'auth_app') THEN
    CREATE ROLE auth_app LOGIN PASSWORD :'auth_password';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'users_app') THEN
    CREATE ROLE users_app LOGIN PASSWORD :'users_password';
  END IF;
  IF NOT EXISTS (SELECT FROM pg_roles WHERE rolname = 'payments_app') THEN
    CREATE ROLE payments_app LOGIN PASSWORD :'payments_password';
  END IF;
END $$;
SELECT 'CREATE DATABASE travel_auth OWNER auth_app' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'travel_auth')\gexec
SELECT 'CREATE DATABASE travel_users OWNER users_app' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'travel_users')\gexec
SELECT 'CREATE DATABASE travel_payments OWNER payments_app' WHERE NOT EXISTS (SELECT FROM pg_database WHERE datname = 'travel_payments')\gexec
REVOKE ALL ON DATABASE travel_auth, travel_users, travel_payments FROM PUBLIC;
SQL
