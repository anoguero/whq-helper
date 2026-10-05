#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
JAVA_DATA="$ROOT_DIR/WhqHelperApp/data"
SPA_DATA="$ROOT_DIR/whq-helper-spa/public/data"

python3 "$ROOT_DIR/scripts/sync_shared_data.py" "$JAVA_DATA" "$SPA_DATA"
