#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
cat scripts/v076chunk.* > /tmp/v076.b64
echo 'e317b451c43dead364e5224ec9ca7551b5ff032abc784201fa80e61f005b31f3  /tmp/v076.b64' | sha256sum -c -
base64 -d /tmp/v076.b64 > /tmp/v076.tar.gz
echo '353193c3a8e0e36cfd45b87a03b88cabeee3d161ed7c026fa50b2603f5fba12e  /tmp/v076.tar.gz' | sha256sum -c -
tar -xzf /tmp/v076.tar.gz -C "$ROOT"
bash ./scripts/check-v076-service-wiring-contract.sh
rm -f scripts/v076chunk.* scripts/bootstrap-v076-tasks1-7.sh
python3 - <<'PY'
from pathlib import Path
p = Path('.github/workflows/build-apk.yml')
s = p.read_text()
s = s.replace('      contents: write\n', '      contents: read\n')
s = s.replace("      - uses: actions/checkout@v4\n        with:\n          ref: feature/v0.7-adaptive-envelope\n", "      - uses: actions/checkout@v4\n")
s = s.replace("      - name: Bootstrap v0.7.6 tasks 1-7\n        run: bash ./scripts/bootstrap-v076-tasks1-7.sh\n", "")
p.write_text(s)
PY
git config user.name 'SoundCeiling CI'
git config user.email 'actions@users.noreply.github.com'
git add -A
git commit -m 'feat: implement v0.7.6 control architecture tasks 1-7'
git push origin HEAD:feature/v0.7-adaptive-envelope
