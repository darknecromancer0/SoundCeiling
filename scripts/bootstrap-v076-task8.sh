#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
echo '19e3fe89cffa02b7751f7b0ff97079cc8e1736bc45ff7593b505fecc88ce90fb  scripts/v076task8.patch.gz.b64' | sha256sum -c -
base64 -d scripts/v076task8.patch.gz.b64 > /tmp/v076task8.patch.gz
echo 'b52a7a27cfdf79d74ed1b089893ba76f1fe898fcdd12f2bc72171e658316f963  /tmp/v076task8.patch.gz' | sha256sum -c -
gzip -dc /tmp/v076task8.patch.gz > /tmp/v076task8.patch
echo 'db99116209f3b321a80c32668b5d84d19fb202c03ee147bc2888958ba9a3b445  /tmp/v076task8.patch' | sha256sum -c -
git apply --check /tmp/v076task8.patch
git apply /tmp/v076task8.patch
./scripts/run-pure-tests.sh
bash ./scripts/check-v071-ui-contract.sh
bash ./scripts/check-v076-control-architecture-contract.sh
rm -f scripts/bootstrap-v076-task8.sh scripts/v076task8.patch.gz.b64
git config user.name 'SoundCeiling CI'
git config user.email 'actions@users.noreply.github.com'
git add -A
git commit -m 'test: enforce v0.7.6 control architecture'
git push origin HEAD:feature/v0.7-adaptive-envelope
