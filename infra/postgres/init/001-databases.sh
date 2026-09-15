#!/usr/bin/env bash
set -Eeuo pipefail

psql --username "$POSTGRES_USER" --dbname postgres <<SQL
CREATE USER auth_app PASSWORD '${AUTH_DB_PASSWORD//\'/\'\'}';
CREATE USER users_app PASSWORD '${USERS_DB_PASSWORD//\'/\'\'}';
CREATE USER payments_app PASSWORD '${PAYMENTS_DB_PASSWORD//\'/\'\'}';
CREATE DATABASE travel_auth OWNER auth_app;
CREATE DATABASE travel_users OWNER users_app;
CREATE DATABASE travel_payments OWNER payments_app;
REVOKE ALL ON DATABASE travel_auth, travel_users, travel_payments FROM PUBLIC;
SQL
