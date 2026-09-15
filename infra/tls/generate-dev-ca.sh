#!/usr/bin/env bash
set -euo pipefail
output_dir="${1:-infra/tls/certs}"
mkdir -p "$output_dir"
openssl req -x509 -newkey rsa:4096 -sha256 -days 825 -nodes \
  -keyout "$output_dir/dev-ca.key" -out "$output_dir/dev-ca.crt" \
  -subj "/CN=Travel Plan Development CA"
openssl req -newkey rsa:2048 -nodes -keyout "$output_dir/gateway.key" \
  -out "$output_dir/gateway.csr" -subj "/CN=localhost"
openssl x509 -req -sha256 -days 398 -in "$output_dir/gateway.csr" \
  -CA "$output_dir/dev-ca.crt" -CAkey "$output_dir/dev-ca.key" -CAcreateserial \
  -out "$output_dir/gateway.crt" -extfile <(printf 'subjectAltName=DNS:localhost,IP:127.0.0.1')
rm -f "$output_dir/gateway.csr" "$output_dir/dev-ca.srl"
echo "Generated development CA and gateway certificate in $output_dir"
