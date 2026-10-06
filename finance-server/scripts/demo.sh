#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
set -a
source .env
set +a
base="http://127.0.0.1:8081/api"
# Solo datos ficticios. curl recibe los secretos por archivo temporal privado, no en argumentos.
config=$(mktemp)
trap 'rm -f "$config"' EXIT
chmod 600 "$config"
printf 'user = "admin:%s"\n' "$FINANCE_ADMIN_PASSWORD" > "$config"
curl --fail --silent --show-error --config "$config" -X PUT "$base/products/account-main" -H 'Content-Type: application/json' --data-binary @examples/account.json
curl --fail --silent --show-error --config "$config" -X PUT "$base/products/card-main" -H 'Content-Type: application/json' --data-binary @examples/card.json
curl --fail --silent --show-error --config "$config" -F file=@examples/account.csv "$base/products/account-main/imports"
curl --fail --silent --show-error --config "$config" -F file=@examples/card.csv "$base/products/card-main/imports"
curl --fail --silent --show-error --config "$config" "$base/analysis/summary?from=2026-09-01&to=2026-09-30"
