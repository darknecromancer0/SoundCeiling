#!/usr/bin/env bash
set -euo pipefail
R="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
# v0.7.7.3 superseded the base64 debug key with the repository-pinned stable development signer.
bash "$R/scripts/check-stable-debug-signing-contract.sh"
echo 'v0.7.7.1+ stable development signing compatibility contract: PASS'
