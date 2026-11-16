#!/usr/bin/env bash
# Property-check the website with Bombadil against the local stub.
set -euo pipefail

root="$(cd "$(dirname "$0")/../.." && pwd)"
cd "$root/client"

if [[ ! -x node_modules/.bin/bombadil ]]; then
  npm install --no-fund --no-audit
fi

node --test ./*.test.cjs

port="${PORT:-4173}"
node "$root/formal/ui/stub-server.mjs" "$port" >/tmp/mineportal-ui-stub.log 2>&1 &
stub_pid=$!
cleanup() {
  kill "$stub_pid" >/dev/null 2>&1 || true
}
trap cleanup EXIT

for _ in $(seq 1 50); do
  if curl --fail --silent --output /dev/null "http://127.0.0.1:${port}/"; then
    break
  fi
  sleep 0.1
done
curl --fail --silent --output /dev/null "http://127.0.0.1:${port}/api/account"

out="${BOMBADIL_OUTPUT:-$(mktemp -d)}"
mkdir -p "$out"
./node_modules/.bin/bombadil browser test \
  --headless \
  --no-sandbox \
  --exit-on-violation \
  --time-limit "${BOMBADIL_TIME_LIMIT:-45s}" \
  --output-path "$out" \
  --output-path-overwrite \
  "http://127.0.0.1:${port}/" \
  "$root/formal/ui/specification.js"
