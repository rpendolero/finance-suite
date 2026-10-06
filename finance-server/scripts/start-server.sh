#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
set -a
source .env
set +a
exec java -jar target/finance-server-0.4.0.jar "$@"
