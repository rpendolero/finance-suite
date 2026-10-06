#!/usr/bin/env bash
set -euo pipefail
cd "$(dirname "$0")/.."
# Archivo separado: el bridge no necesita contraseña de administración ni MySQL.
set -a
source private/bridge.env
set +a
exec node scripts/openclaw-bridge.mjs
