#!/usr/bin/env bash
set -euo pipefail
ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WF="$ROOT/.github/workflows/build-apk.yml"
README="$ROOT/README.md"
CHECKLIST="$ROOT/docs/field-tests/2026-08-23-v0.7.3-samsung-corrective-checklist.md"
fail(){ echo "v0.7.3 release contract: $*" >&2; exit 1; }
require(){ local f="$1" n="$2"; [[ -f "$f" ]] || fail "missing $(basename "$f")"; grep -Fq -- "$n" "$f" || fail "missing $(basename "$f") -> $n"; }
require_order(){ local f="$1" a="$2" b="$3" la lb; require "$f" "$a"; require "$f" "$b"; la=$(grep -Fn -- "$a" "$f"|head -1|cut -d: -f1); lb=$(grep -Fn -- "$b" "$f"|head -1|cut -d: -f1); [[ $la -lt $lb ]] || fail "expected order: $a before $b"; }
require "$WF" 'run: bash ./scripts/check-v073-corrective-contract.sh'
require "$WF" 'run: bash ./scripts/check-v073-release-contract.sh'
require "$WF" 'path: app/build/outputs/apk/debug/app-debug.apk'
require_order "$WF" 'run: bash ./scripts/check-v072-release-contract.sh' 'run: bash ./scripts/check-v073-corrective-contract.sh'
require_order "$WF" 'run: bash ./scripts/check-v073-corrective-contract.sh' 'run: bash ./scripts/check-v073-release-contract.sh'
require_order "$WF" 'run: bash ./scripts/check-v073-release-contract.sh' 'run: ./gradlew --no-daemon --stacktrace :app:assembleDebug'
require "$README" '## v0.7.3 corrective highlights'
require "$README" 'Media UP is debt-only'
require "$CHECKLIST" 'awaiting device test'
require "$CHECKLIST" 'Разрешить распознавание источника для DSP'
require "$CHECKLIST" 'global_dsp_transport'
require "$CHECKLIST" 'APK SHA-256'
echo "v0.7.3 release contract: PASS"
