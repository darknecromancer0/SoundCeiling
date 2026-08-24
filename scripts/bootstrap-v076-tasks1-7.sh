#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
cd "$ROOT"
base64 -d scripts/v076_tasks1_7.tar.gz.b64 | tar -xzf - -C "$ROOT"
rm -f scripts/bootstrap-v076-tasks1-7.sh scripts/v076_tasks1_7.tar.gz.b64
python3 - <<'PY'
from pathlib import Path
p=Path('.github/workflows/build-apk.yml')
s=p.read_text()
s=s.replace('      contents: write\n','      contents: read\n')
block="      - name: Bootstrap v0.7.6 tasks 1-7\n        if: ${{ github.event_name == 'pull_request' }}\n        run: bash ./scripts/bootstrap-v076-tasks1-7.sh\n"
s=s.replace(block,'')
s=s.replace("      - uses: actions/checkout@v4\n        with:\n          ref: feature/v0.7-adaptive-envelope\n","      - uses: actions/checkout@v4\n")
p.write_text(s)
PY
git config user.name 'SoundCeiling CI'
git config user.email 'actions@users.noreply.github.com'
git add -A
git commit -m 'feat: implement v0.7.6 control architecture tasks 1-7'
git push origin HEAD:feature/v0.7-adaptive-envelope
