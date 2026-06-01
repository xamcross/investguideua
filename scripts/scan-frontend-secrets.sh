#!/usr/bin/env bash
#
# QA1 AC #10 secret scan (Linux/CI counterpart of scan-frontend-secrets.ps1).
# Fails (exit 1) if any secret leaked into the built Angular bundle (frontend/dist).
# SPECIFICATION section 10, AC #10. Run after `npm run build`.
#
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
dist_path="${1:-$repo_root/frontend/dist}"

if [ ! -d "$dist_path" ]; then
  echo "Bundle not found at '$dist_path'. Build the frontend first (npm run build) then re-run." >&2
  exit 2
fi

# Static markers that must never appear in a client bundle.
static_patterns=(
  'sk-ant-'
  'BEGIN PRIVATE KEY'
  'BEGIN RSA PRIVATE KEY'
  'ANTHROPIC_API_KEY'
  'LIQPAY_PRIVATE_KEY'
  'JWT_SECRET'
  'mongodb://[^"'"'"']*:[^"'"'"']*@'
)

secret_env_names=(ANTHROPIC_API_KEY LIQPAY_PRIVATE_KEY LIQPAY_PUBLIC_KEY JWT_SECRET MONGODB_URI)

violations=0

scan_target=$(find "$dist_path" -type f \
  \( -name '*.js' -o -name '*.mjs' -o -name '*.css' -o -name '*.html' -o -name '*.json' -o -name '*.txt' -o -name '*.map' \))

for pat in "${static_patterns[@]}"; do
  if echo "$scan_target" | xargs -r grep -nIE "$pat" 2>/dev/null; then
    echo "AC #10 FAILED - marker /$pat/ found above." >&2
    violations=$((violations + 1))
  fi
done

# Load populated values from .env (if present) so the live secret values are also scanned for.
env_file="$repo_root/.env"
load_env_value() {
  local name="$1"
  local val="${!name:-}"
  if [ -z "$val" ] && [ -f "$env_file" ]; then
    val="$(grep -E "^[[:space:]]*$name[[:space:]]*=" "$env_file" | head -n1 | sed -E "s/^[^=]*=[[:space:]]*//; s/[[:space:]]*$//")"
  fi
  printf '%s' "$val"
}

for name in "${secret_env_names[@]}"; do
  val="$(load_env_value "$name")"
  # Skip empty and short/placeholder values to avoid false positives (e.g. localhost Mongo URI).
  if [ -n "$val" ] && [ "${#val}" -ge 12 ]; then
    if echo "$scan_target" | xargs -r grep -nIF "$val" 2>/dev/null; then
      echo "AC #10 FAILED - live value of $name found above." >&2
      violations=$((violations + 1))
    fi
  fi
done

if [ "$violations" -gt 0 ]; then
  echo "AC #10 FAILED - $violations secret leak(s) in the frontend bundle." >&2
  exit 1
fi

count=$(echo "$scan_target" | grep -c . || true)
echo "AC #10 OK - scanned $count bundle file(s); no secrets found."
exit 0
