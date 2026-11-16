#!/usr/bin/env bash
# Download the pinned TLC release if needed and check every model in formal/.
set -euo pipefail

root="$(cd "$(dirname "$0")/.." && pwd)"
jar="${TLA_TOOLS_JAR:-"$root/.tla/tla2tools.jar"}"
url="https://github.com/tlaplus/tlaplus/releases/download/v1.7.4/tla2tools.jar"
sum="936a262061c914694dfd669a543be24573c45d5aa0ff20a8b96b23d01e050e88"

if [[ ! -f "$jar" ]]; then
  mkdir -p "$(dirname "$jar")"
  curl --fail --location --silent --show-error --output "$jar" "$url"
fi
echo "$sum  $jar" | sha256sum --check --strict

cd "$root"
configs=(
  formal/ProtocolConnection.cfg
  formal/DesktopPairing.cfg
  formal/DeviceGrant.cfg
  formal/PlaySession.cfg
  formal/ServerAttempt.cfg
  formal/RelayAnnounce.cfg
)
for config in "${configs[@]}"; do
  module="${config%.cfg}.tla"
  echo "== TLC $module"
  java -jar "$jar" -workers auto -cleanup -config "$config" "$module"
done
