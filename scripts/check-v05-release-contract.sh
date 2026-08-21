#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKFLOW="$ROOT/.github/workflows/build-apk.yml"
README="$ROOT/README.md"

require(){ local file="$1"; local needle="$2"; grep -Fq "$needle" "$file" || { echo "Missing historical v0.5 release regression: $(basename "$file") -> $needle" >&2; exit 1; }; }

# Historical v0.5 architecture gate. It deliberately does not constrain the
# current app version or artifact name; v0.6 must keep these proven gates alive.
require "$README" 'v0.5.1'
for gate in \
  './scripts/run-pure-tests.sh' \
  './scripts/check-v04-storage-contract.sh' \
  './scripts/check-v05-storage-contract.sh' \
  './scripts/check-v05-app-contract.sh' \
  './scripts/check-source-invariants.sh' \
  './scripts/check-v05-pcm-contract.sh' \
  './scripts/check-v05-microphone-invariant.sh' \
  './scripts/check-v05-control-adapters.sh' \
  './scripts/check-ui-contract.sh' \
  './scripts/check-v04-ui-contract.sh' \
  './scripts/check-v05-ui-contract.sh' \
  './scripts/check-v04-package-contract.sh' \
  './scripts/check-v05-release-contract.sh' \
  ':app:assembleDebug'; do
  require "$WORKFLOW" "$gate"
done

if grep -Fq 'SoundCeiling-v0.4.0-debug-apk' "$WORKFLOW"; then
  echo 'Stale v0.4 artifact name remains in workflow' >&2
  exit 1
fi

echo "v0.5 historical release regression: PASS"
