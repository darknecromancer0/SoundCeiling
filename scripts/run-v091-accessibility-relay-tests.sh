#!/usr/bin/env bash
set -euo pipefail

R="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

bash "$R/scripts/run-v091-relay-gate-tests.sh"
bash "$R/scripts/run-v091-relay-lease-volume-tests.sh"
bash "$R/scripts/run-v091-relay-preflight-latency-tests.sh"
bash "$R/scripts/run-v091-relay-topology-tests.sh"
bash "$R/scripts/run-v091-relay-pcm-tests.sh"
bash "$R/scripts/check-v091-renderer-contract.sh"
bash "$R/scripts/check-v091-playback-ownership-contract.sh"
bash "$R/scripts/check-v091-accessibility-key-contract.sh"
bash "$R/scripts/check-v091-runtime-wiring-contract.sh"
bash "$R/scripts/check-v091-relay-ui-contract.sh"

echo 'v0.9.1 Accessibility Relay aggregate: PASS'
