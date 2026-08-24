#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BUILD="$ROOT/app/build.gradle.kts"
WF="$ROOT/.github/workflows/build-apk.yml"
README="$ROOT/README.md"
CHECKLIST="$ROOT/docs/field-tests/2026-08-25-v0.7.6.2-samsung-checklist.md"
fail(){ echo "v0.7.6.2 release contract: $*" >&2; exit 1; }
require(){ local f="$1" n="$2"; [[ -f "$f" ]] || fail "missing $(basename "$f")"; grep -Fq -- "$n" "$f" || fail "missing $(basename "$f") -> $n"; }
require_order(){ local f="$1" a="$2" b="$3" la lb; require "$f" "$a"; require "$f" "$b"; la=$(grep -Fn -- "$a" "$f"|head -1|cut -d: -f1); lb=$(grep -Fn -- "$b" "$f"|head -1|cut -d: -f1); [[ $la -lt $lb ]] || fail "expected order: $a before $b"; }
python - "$BUILD" <<'PYVER'
from pathlib import Path
import re, sys
s = Path(sys.argv[1]).read_text()
m = re.search(r'versionCode=(\d+)', s)
if not m or int(m.group(1)) < 22:
    raise SystemExit('v0.7.6.2 release contract: expected versionCode >= 22')
PYVER
require "$WF" 'run: bash ./scripts/check-v0762-output-domain-contract.sh'
require "$WF" 'run: bash ./scripts/check-v0762-release-contract.sh'
require_order "$WF" 'run: bash ./scripts/check-v0762-output-domain-contract.sh' 'run: bash ./scripts/check-v0762-release-contract.sh'
require_order "$WF" 'run: bash ./scripts/check-v0762-release-contract.sh' 'run: ./gradlew --no-daemon --stacktrace :app:assembleDebug'
require "$README" '## v0.7.6.2 Samsung output-domain corrective'
require "$README" 'PRE_VOLUME PCM outranks Visualizer for normalizer control'
require "$README" 'Visualizer-only UNKNOWN evidence is fail-closed'
require "$CHECKLIST" 'No Visualizer-driven volume sink'
require "$CHECKLIST" 'meterDomain=PROJECTED'
require "$CHECKLIST" 'coarse_no_output_loudness'
require "$CHECKLIST" 'bounded -0.5 dB differential probe'
require "$CHECKLIST" 'APK SHA-256'

echo 'v0.7.6.2 release contract: PASS'
